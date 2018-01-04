package dcf_user;

import javax.xml.soap.SOAPException;

import catalogue_generator.ThreadFinishedListener;

/**
 * Thread to re-authenticate the user using previous credentials stored in the database.
 * @author avonva
 *
 */
public class ReauthThread extends Thread {

	private ThreadFinishedListener doneListener;

	public void setDoneListener(ThreadFinishedListener doneListener) {
		this.doneListener = doneListener;
	}

	@Override
	public void run() {

		int code;
		Exception exception = null;
		
		// try to reauthenticate the user if possible
		boolean done;
		try {
			done = User.getInstance().reauthenticate();
			code = done ? ThreadFinishedListener.OK : ThreadFinishedListener.ERROR;
		} catch (SOAPException e) {
			e.printStackTrace();
			code = ThreadFinishedListener.EXCEPTION;
			exception = e;
		}

		if (doneListener != null) {
			this.doneListener.finished(this, code, exception);
		}
	}
}