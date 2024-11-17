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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import de.ovgu.featureide.fm.core.Logger;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import de.ovgu.featureide.fm.core.job.util.JobFinishListener;

/**
 * Job that wraps the functionality of a {@link LongRunningMethod}.
 *
 * @author Sebastian Krieter
 */
// TODO Implement prioritization
public class LongRunningThread<T> extends Thread implements IRunner<T> {

	protected final List<JobFinishListener<T>> listenerList = new LinkedList<>();

	private final LongRunningMethod<T> method;
	private final IMonitor<T> monitor;
	private Executer<T> executer;

	private int cancelingTime = -1;
	private int timeout = -1;

	private T methodResult = null;
	private JobStatus status = JobStatus.NOT_STARTED;

	private boolean stoppable;

	public LongRunningThread(String name, LongRunningMethod<T> method, IMonitor<T> monitor) {
		super(name);
		this.method = method;
		this.monitor = monitor != null ? monitor : new NullMonitor<T>();
	}

	@Override
	public void addJobFinishedListener(JobFinishListener<T> listener) {
		if (!listenerList.contains(listener)) {
			listenerList.add(listener);
		}
	}

	@Override
	public boolean cancel() {
		if (executer != null) {
			executer.cancel();
		}
		return !isAlive();
	}

	public void fireEvent() {
		for (final JobFinishListener<T> listener : listenerList) {
			try {
				listener.jobFinished(this);
			} catch (final Throwable e) {
				Logger.logError(e);
			}
		}
	}

	@Override
	public int getCancelingTime() {
		return cancelingTime;
	}

	@Override
	public int getTimeout() {
		return timeout;
	}

	@Override
	public T getResults() {
		return methodResult;
	}

	@Override
	public LongRunningMethod<T> getMethod() {
		return method;
	}

	@Override
	public final JobStatus getStatus() {
		return status;
	}

	@Override
	public boolean isStoppable() {
		return stoppable;
	}

	@Override
	public void removeJobFinishedListener(JobFinishListener<T> listener) {
		listenerList.remove(listener);
	}

	@Override
	public void run() {
		// TODO check for cancel at beginning
		status = JobStatus.RUNNING;
		try {
			executer = stoppable ? new StoppableExecuter<>(method, timeout, cancelingTime) : new Executer<>(method);
			methodResult = executer.execute(monitor);
			status = JobStatus.OK;
		} catch (final Exception e) {
			Logger.logError(e);
			status = JobStatus.FAILED;
		} finally {
			monitor.done();
			for (final JobFinishListener<T> listener : listenerList) {
				try {
					listener.jobFinished(this);
				} catch (final Throwable e) {
					Logger.logError(e);
				}
			}
		}
	}

	@Override
	public void schedule() {
		start();
	}

	@Override
	public void setCancelingTime(int cancelingTime) {
		this.cancelingTime = cancelingTime;
	}

	@Override
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	@Override
	public void setIntermediateFunction(Consumer<T> intermediateFunction) {
		monitor.setIntermediateFunction(intermediateFunction);
	}

	@Override
	public void setStoppable(boolean stoppable) {
		this.stoppable = stoppable;
	}

}
