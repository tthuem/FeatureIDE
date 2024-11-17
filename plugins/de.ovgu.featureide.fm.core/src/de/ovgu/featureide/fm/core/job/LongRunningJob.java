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

import org.eclipse.core.runtime.jobs.Job;

import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

/**
 * Job that wraps the functionality of a {@link LongRunningMethod}.
 *
 * @author Sebastian Krieter
 */
public class LongRunningJob<T> extends AbstractJob<T> implements IRunner<T> {

	private final LongRunningMethod<T> method;

	private Executer<T> executer;

	private int cancelingTime = -1;
	private int timeout = -1;

	private boolean stoppable;

	public LongRunningJob(String name, LongRunningMethod<T> method) {
		super(name, Job.LONG);
		this.method = method;
	}

	@Override
	protected T work(IMonitor<T> monitor) throws Exception {
		executer = stoppable ? new StoppableExecuter<>(method, timeout, cancelingTime) : new Executer<>(method);
		methodResult = executer.execute(monitor);
		return methodResult;
	}

	@Override
	protected void canceling() {
		if (executer != null) {
			executer.cancel();
		}
		super.canceling();
	}

	@Override
	public LongRunningMethod<T> getMethod() {
		return method;
	}

	@Override
	public boolean isStoppable() {
		return stoppable;
	}

	@Override
	public void setStoppable(boolean stoppable) {
		this.stoppable = stoppable;
	}

	@Override
	public int getCancelingTime() {
		return cancelingTime;
	}

	@Override
	public void setCancelingTime(int cancelingTime) {
		this.cancelingTime = cancelingTime;
	}

	@Override
	public int getTimeout() {
		return timeout;
	}

	@Override
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

}
