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

/**
 * An interface for executing a {@link LongRunningMethod long running method} within a separate thread.
 *
 * @author Sebastian Krieter
 */
public interface IRunner<T> extends IJob<T> {

	/**
	 * Time to wait after canceling the execution before the executing thread is stopped forcefully.
	 *
	 * @return time in ms
	 */
	int getCancelingTime();

	/**
	 * Time after which the execution is canceled.
	 *
	 * @return time in ms
	 */
	int getTimeout();

	/**
	 * Whether the runner can be stopped by a timeout or manually.
	 *
	 * @return if {@code true} the runner can be stopped.
	 */
	boolean isStoppable();

	/**
	 * Time to wait after canceling the execution before the executing thread is stopped forcefully.
	 *
	 * @param cancelingTime time in ms
	 */
	void setCancelingTime(int cancelingTime);

	/**
	 * Time after which the execution is canceled.
	 *
	 * @param timeout time in ms
	 */
	void setTimeout(int timeout);

	/**
	 * Determines, whether the runner can be stopped by a timeout or manually.
	 *
	 * @param stoppable if {@code true} the runner can be stopped.
	 */
	void setStoppable(boolean stoppable);

	/**
	 * Returns the method to be executed.
	 *
	 * @return the method
	 */
	LongRunningMethod<T> getMethod();

}
