/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2019  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core.job;

import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor.MethodCancelException;

/**
 * Abstract eclipse job which can be stopped.
 *
 * @author Sebastian Krieter
 */
class StoppableExecuter<T> extends Executer<T> {

	/**
	 * Default canceling timeout of 300 milliseconds.
	 */
	static final int DEFAULT_CANCELING_TIME = 300;

	private class InnerThread extends Thread {

		private T result = null;
		private Exception exception = null;

		@Override
		public void run() {
			try {
				result = method.execute(monitor);
			} catch (final MethodCancelException e) {
				exception = e;
			} catch (final Exception e) {
				exception = e;
				Logger.logError(e);
			}
		}

	}

	private final int cancelingTime;
	private final int timeout;
	private boolean canceled = false;

	private InnerThread innerThread = null;

	/**
	 * Creates a new stoppable executor. If the provided canceling timeout is smaller then 0, the {@link #DEFAULT_CANCELING_TIME default} is used.
	 *
	 * @param method the method to run. Must not be {@code null}.
	 * @param timeout time in ms after which the execution is canceled.
	 * @param cancelingTime time in ms to wait after canceling the execution before the thread is stopped forcefully.
	 */
	public StoppableExecuter(LongRunningMethod<T> method, int timeout, int cancelingTime) {
		super(method);
		this.cancelingTime = (cancelingTime < 0) ? DEFAULT_CANCELING_TIME : cancelingTime;
		this.timeout = (timeout <= 0) ? -1 : timeout;

	}

	/**
	 * Creates a new stoppable executor. Uses the {@link #DEFAULT_CANCELING_TIME default} canceling time and no timeout.
	 *
	 * @param method the method to run. Must not be {@code null}.
	 */
	public StoppableExecuter(LongRunningMethod<T> method) {
		this(method, -1, -1);
	}

	@Override
	public final void cancel() {
		synchronized (this) {
			if (canceled || (innerThread == null) || !innerThread.isAlive()) {
				return;
			}
			canceled = true;
		}

		monitor.cancel();

		if (cancelingTime > 0) {
			try {
				innerThread.join(cancelingTime);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		stopInnerThread();
	}

	@SuppressWarnings("deprecation")
	private void stopInnerThread() {
		try {
			synchronized (this) {
				if (innerThread.isAlive()) {
					innerThread.stop();
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public T execute(IMonitor<T> monitor) throws Exception {
		synchronized (this) {
			// in case job was started and canceled at the same time
			this.monitor = monitor;
			monitor.checkCancel();
			canceled = false;
			innerThread = new InnerThread();
			innerThread.start();
		}
		try {
			if (timeout > 0) {
				innerThread.join(timeout);
				cancel();
			} else {
				innerThread.join();
			}
			if (innerThread.exception != null) {
				throw innerThread.exception;
			}
			return innerThread.result;
		} catch (final InterruptedException e) {
			Logger.logError(e);
			stopInnerThread();
			return null;
		} catch (final MethodCancelException e) {
			return null;
		} finally {
			synchronized (this) {
				innerThread = null;
			}
			monitor.done();
		}
	}
}
