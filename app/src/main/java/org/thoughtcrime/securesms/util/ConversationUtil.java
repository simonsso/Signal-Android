package org.thoughtcrime.securesms.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.notifications.SingleRecipientNotificationBuilder;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ConversationUtil encapsulates support for Android 11+'s new Conversations system
 */
public final class ConversationUtil {

  public static final int CONVERSATION_SUPPORT_VERSION  = 30;

  private ConversationUtil() {}


  /**
   * @return The stringified channel id for a given Recipient
   */
  @WorkerThread
  public static @NonNull String getChannelId(@NonNull Context context, @NonNull Recipient recipient) {
    Recipient resolved =  recipient.resolve();

    return resolved.getNotificationChannel() != null ? resolved.getNotificationChannel() : NotificationChannels.getMessagesChannel(context);
  }

  /**
   * Pushes a new dynamic shortcut for the given recipient and updates the ranks of all current
   * shortcuts.
   */
  public static void pushShortcutForRecipient(@NonNull Context context, @NonNull Recipient recipient) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      SignalExecutors.BOUNDED.execute(() -> {
        pushShortcutAndUpdateRanks(context, recipient);
      });
    }
  }

  /**
   * Synchronously pushes a new dynamic shortcut for the given recipient if one does not already exist.
   *
   * If added, this recipient is given a high ranking with the intention of not appearing immediately in results.
   */
  @WorkerThread
  public static void pushShortcutForRecipientIfNeededSync(@NonNull Context context, @NonNull Recipient recipient) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      ShortcutManager    shortcutManager = ServiceUtil.getShortcutManager(context);
      String             shortcutId      = getShortcutId(recipient);
      List<ShortcutInfo> shortcuts       = shortcutManager.getDynamicShortcuts();

      boolean hasPushedRecipientShortcut = Stream.of(shortcuts)
                                                 .filter(info -> Objects.equals(shortcutId, info.getId()))
                                                 .findFirst()
                                                 .isPresent();

      if (!hasPushedRecipientShortcut) {
        pushShortcutForRecipientInternal(context, recipient, shortcuts.size());
      }
    }
  }

  /**
   * Clears all currently set dynamic shortcuts
   */
  public static void clearAllShortcuts(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      ShortcutManager    shortcutManager = ServiceUtil.getShortcutManager(context);
      List<ShortcutInfo> shortcutInfos   = shortcutManager.getDynamicShortcuts();

      shortcutManager.removeLongLivedShortcuts(Stream.of(shortcutInfos).map(ShortcutInfo::getId).toList());
    }
  }

  /**
   * Clears the shortcuts tied to a given thread.
   */
  public static void clearShortcuts(@NonNull Context context, @NonNull Set<Long> threadIds) {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      SignalExecutors.BOUNDED.execute(() -> {
        List<RecipientId> recipientIds    = DatabaseFactory.getThreadDatabase(context).getRecipientIdsForThreadIds(threadIds);
        ShortcutManager   shortcutManager = ServiceUtil.getShortcutManager(context);

        shortcutManager.removeLongLivedShortcuts(Stream.of(recipientIds).map(ConversationUtil::getShortcutId).toList());
      });
    }
  }

  /**
   * Returns an ID that is unique between all recipients.
   *
   * @param recipientId The recipient ID to get a shortcut ID for
   *
   * @return A unique identifier that is stable for a given recipient id
   */
  public static @NonNull String getShortcutId(@NonNull RecipientId recipientId) {
    return recipientId.serialize();
  }

  /**
   * Returns an ID that is unique between all recipients.
   *
   * @param recipient The recipient to get a shortcut for.
   *
   * @return A unique identifier that is stable for a given recipient id
   */
  public static @NonNull String getShortcutId(@NonNull Recipient recipient) {
    return getShortcutId(recipient.getId());
  }

  /**
   * Updates the rank of each existing shortcut by 1 and then publishes a new shortcut of rank 0
   * for the given recipient.
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static void pushShortcutAndUpdateRanks(@NonNull Context context, @NonNull Recipient recipient) {
    ShortcutManager    shortcutManager  = ServiceUtil.getShortcutManager(context);
    List<ShortcutInfo> currentShortcuts = shortcutManager.getDynamicShortcuts();

    if (Util.hasItems(currentShortcuts)) {
      for (ShortcutInfo shortcutInfo : currentShortcuts) {
        RecipientId  recipientId = RecipientId.from(shortcutInfo.getId());
        Recipient    resolved    = Recipient.resolved(recipientId);
        ShortcutInfo updated     = buildShortcutInfo(context, resolved, shortcutInfo.getRank() + 1);

        shortcutManager.pushDynamicShortcut(updated);
      }
    }

    pushShortcutForRecipientInternal(context, recipient, 0);
  }

  /**
   * Pushes a dynamic shortcut for a given recipient to the shortcut manager
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static void pushShortcutForRecipientInternal(@NonNull Context context, @NonNull Recipient recipient, int rank) {
    ShortcutInfo    shortcutInfo    = buildShortcutInfo(context, recipient, rank);
    ShortcutManager shortcutManager = ServiceUtil.getShortcutManager(context);

    shortcutManager.pushDynamicShortcut(shortcutInfo);
  }

  /**
   * Builds the shortcut info object for a given Recipient.
   *
   * @param context   The Context under which we are operating
   * @param recipient The Recipient to generate a ShortcutInfo for
   * @param rank      The rank that should be assigned to this recipient
   * @return          The new ShortcutInfo
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static @NonNull ShortcutInfo buildShortcutInfo(@NonNull Context context,
                                                         @NonNull Recipient recipient,
                                                         int rank)
  {
    Recipient resolved  = recipient.resolve();
    Person[]  persons   = buildPersons(context, resolved);
    long      threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(resolved);
    String    shortName = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getShortDisplayName(context);
    String    longName  = resolved.isSelf() ? context.getString(R.string.note_to_self) : resolved.getDisplayName(context);

    return new ShortcutInfo.Builder(context, getShortcutId(resolved))
                           .setLongLived(true)
                           .setIntent(ConversationIntents.createBuilder(context, resolved.getId(), threadId).build())
                           .setShortLabel(shortName)
                           .setLongLabel(longName)
                           .setIcon(AvatarUtil.getIconForShortcut(context, resolved))
                           .setPersons(persons)
                           .setCategories(Collections.singleton(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                           .setActivity(new ComponentName(context, "org.thoughtcrime.securesms.RoutingActivity"))
                           .setRank(rank)
                           .build();
  }

  /**
   * @return an array of Person objects correlating to members of a conversation (other than self)
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static @NonNull Person[] buildPersons(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.isGroup()) {
      return buildPersonsForGroup(context, recipient.getGroupId().get());
    } else {
      return new Person[]{buildPerson(context, recipient)};
    }
  }

  /**
   * @return an array of Person objects correlating to members of a group (other than self)
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static @NonNull Person[] buildPersonsForGroup(@NonNull Context context, @NonNull GroupId groupId) {
    List<Recipient> members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

    return Stream.of(members).map(member -> buildPerson(context, member.resolve())).toArray(Person[]::new);
  }

  /**
   * @return A Person object representing the given Recipient
   */
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  @WorkerThread
  private static @NonNull Person buildPerson(@NonNull Context context, @NonNull Recipient recipient) {
    return new Person.Builder()
                     .setKey(getShortcutId(recipient.getId()))
                     .setName(recipient.getDisplayName(context))
                     .setUri(recipient.isSystemContact() ? recipient.getContactUri().toString() : null)
                     .build();
  }

  /**
   * @return A Compat Library Person object representing the given Recipient
   */
  @WorkerThread
  public static @NonNull androidx.core.app.Person buildPersonCompat(@NonNull Context context, @NonNull Recipient recipient) {
    return new androidx.core.app.Person.Builder()
                                       .setKey(getShortcutId(recipient.getId()))
                                       .setName(recipient.getDisplayName(context))
                                       .setIcon(AvatarUtil.getIconForNotification(context, recipient))
                                       .setUri(recipient.isSystemContact() ? recipient.getContactUri().toString() : null)
                                       .build();
  }
}
