package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.thoughtcrime.securesms.conversation.ConversationDataSource.Invalidator;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaRepository;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.Pair;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class ConversationViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationViewModel.class);

  private final Application                        context;
  private final MediaRepository                    mediaRepository;
  private final ConversationRepository             conversationRepository;
  private final MutableLiveData<List<Media>>       recentMedia;
  private final MutableLiveData<Long>              threadId;
  private final LiveData<PagedList<MessageRecord>> messages;
  private final LiveData<ConversationData>         conversationMetadata;
  private final Invalidator                        invalidator;

  private int jumpToPosition;

  private ConversationViewModel() {
    this.context                = ApplicationDependencies.getApplication();
    this.mediaRepository        = new MediaRepository();
    this.conversationRepository = new ConversationRepository();
    this.recentMedia            = new MutableLiveData<>();
    this.threadId               = new MutableLiveData<>();
    this.invalidator            = new Invalidator();

    LiveData<ConversationData> metadata = Transformations.switchMap(threadId, thread -> {
      LiveData<ConversationData> conversationData = conversationRepository.getConversationData(thread, jumpToPosition);

      jumpToPosition = -1;

      return conversationData;
    });

    LiveData<Pair<Long, PagedList<MessageRecord>>> messagesForThreadId = Transformations.switchMap(metadata, data -> {
      DataSource.Factory<Integer, MessageRecord> factory = new ConversationDataSource.Factory(context, data.getThreadId(), invalidator);
      PagedList.Config                           config  = new PagedList.Config.Builder()
                                                                               .setPageSize(25)
                                                                               .setInitialLoadSizeHint(25)
                                                                               .build();

      final int startPosition;
      if (data.shouldJumpToMessage()) {
        startPosition = data.getJumpToPosition();
      } else {
        startPosition = data.getLastSeenPosition();
      }

      Log.d(TAG, "Starting at position " + startPosition + " :: " + data.getJumpToPosition() + " :: " + data.getLastSeenPosition());

      return Transformations.map(new LivePagedListBuilder<>(factory, config).setFetchExecutor(ConversationDataSource.EXECUTOR)
                                                                            .setInitialLoadKey(Math.max(startPosition, 0))
                                                                            .build(),
                                 input -> new Pair<>(data.getThreadId(), input));
    });

    this.messages = Transformations.map(messagesForThreadId, Pair::second);

    LiveData<Long> distinctThread = Transformations.distinctUntilChanged(threadId);

    conversationMetadata = Transformations.switchMap(distinctThread, thread -> metadata);
  }

  void onAttachmentKeyboardOpen() {
    mediaRepository.getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, recentMedia::postValue);
  }

  void onConversationDataAvailable(long threadId, int startingPosition) {
    Log.d(TAG, "[onConversationDataAvailable] threadId: " + threadId + ", startingPosition: " + startingPosition);
    this.jumpToPosition = startingPosition;

    this.threadId.setValue(threadId);
  }

  @NonNull LiveData<List<Media>> getRecentMedia() {
    return recentMedia;
  }

  @NonNull LiveData<ConversationData> getConversationMetadata() {
    return conversationMetadata;
  }

  @NonNull LiveData<PagedList<MessageRecord>> getMessages() {
    return messages;
  }

  long getLastSeen() {
    return conversationMetadata.getValue() != null ? conversationMetadata.getValue().getLastSeen() : 0;
  }

  int getLastSeenPosition() {
    return conversationMetadata.getValue() != null ? conversationMetadata.getValue().getLastSeenPosition() : 0;
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    invalidator.invalidate();
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationViewModel());
    }
  }
}
