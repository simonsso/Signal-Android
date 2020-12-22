package org.thoughtcrime.securesms.jobs;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData;

/**
 * Runs in the same queue as messages for the group.
 */
final class GroupCallPeekWorkerJob extends BaseJob {

  public static final String KEY = "GroupCallPeekWorkerJob";

  private static final String KEY_SENDER                    = "sender";
  private static final String KEY_GROUP_RECIPIENT_ID        = "group_recipient_id";
  private static final String KEY_GROUP_CALL_ERA_ID         = "group_call_era_id";
  private static final String KEY_SERVER_RECEIVED_TIMESTAMP = "server_timestamp";

  @NonNull private final WebRtcData.GroupCallUpdateMetadata updateMetadata;

  public GroupCallPeekWorkerJob(@NonNull WebRtcData.GroupCallUpdateMetadata updateMetadata) {
    this(new Parameters.Builder()
                       .setQueue(PushProcessMessageJob.getQueueName(updateMetadata.getGroupRecipientId()))
                       .build(),
         updateMetadata);
  }

  private GroupCallPeekWorkerJob(@NonNull Parameters parameters,
                                 @NonNull WebRtcData.GroupCallUpdateMetadata updateMetadata)
  {
    super(parameters);
    this.updateMetadata = updateMetadata;
  }

  @Override
  protected void onRun() {
    Intent intent = new Intent(context, WebRtcCallService.class);

    intent.setAction(WebRtcCallService.ACTION_GROUP_CALL_UPDATE_MESSAGE)
          .putExtra(WebRtcCallService.EXTRA_GROUP_CALL_UPDATE_SENDER, updateMetadata.getSender().serialize())
          .putExtra(WebRtcCallService.EXTRA_GROUP_CALL_UPDATE_GROUP, updateMetadata.getGroupRecipientId().serialize())
          .putExtra(WebRtcCallService.EXTRA_GROUP_CALL_ERA_ID, updateMetadata.getGroupCallEraId())
          .putExtra(WebRtcCallService.EXTRA_SERVER_RECEIVED_TIMESTAMP, updateMetadata.getServerReceivedTimestamp());

    context.startService(intent);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putString(KEY_SENDER, updateMetadata.getSender().serialize())
                   .putString(KEY_GROUP_RECIPIENT_ID, updateMetadata.getGroupRecipientId().serialize())
                   .putString(KEY_GROUP_CALL_ERA_ID, updateMetadata.getGroupCallEraId())
                   .putLong(KEY_SERVER_RECEIVED_TIMESTAMP, updateMetadata.getServerReceivedTimestamp())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupCallPeekWorkerJob> {

    @Override
    public @NonNull GroupCallPeekWorkerJob create(@NonNull Parameters parameters, @NonNull Data data) {
      RecipientId sender          = RecipientId.from(data.getString(KEY_SENDER));
      RecipientId group           = RecipientId.from(data.getString(KEY_GROUP_RECIPIENT_ID));
      String      era             = data.getString(KEY_GROUP_CALL_ERA_ID);
      long        serverTimestamp = data.getLong(KEY_SERVER_RECEIVED_TIMESTAMP);

      return new GroupCallPeekWorkerJob(parameters, new WebRtcData.GroupCallUpdateMetadata(sender, group, era, serverTimestamp));
    }
  }
}
