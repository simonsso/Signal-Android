package org.thoughtcrime.securesms.components.identity;


import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class UntrustedSendDialog extends AlertDialog.Builder implements DialogInterface.OnClickListener {

  private final List<IdentityRecord> untrustedRecords;
  private final ResendListener       resendListener;

  public UntrustedSendDialog(@NonNull Context context,
                             @NonNull String message,
                             @NonNull List<IdentityRecord> untrustedRecords,
                             @NonNull ResendListener resendListener)
  {
    super(context);
    this.untrustedRecords = untrustedRecords;
    this.resendListener   = resendListener;

    setTitle(R.string.UntrustedSendDialog_send_message);
    setIcon(R.drawable.ic_warning);
    setMessage(message);
    setPositiveButton(R.string.UntrustedSendDialog_send, this);
    setNegativeButton(android.R.string.cancel, null);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(getContext());

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        synchronized (SESSION_LOCK) {
          for (IdentityRecord identityRecord : untrustedRecords) {
            identityDatabase.setApproval(identityRecord.getRecipientId(), true);
          }
        }

        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        resendListener.onResendMessage();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public interface ResendListener {
    public void onResendMessage();
  }
}
