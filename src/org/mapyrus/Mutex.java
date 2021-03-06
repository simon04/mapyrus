/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 - 2013 Simon Chenery.
 *
 * Mapyrus is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mapyrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mapyrus; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.mapyrus;

/**
 * A mutex, for synchronising different threads.
 */
public class Mutex
{
	private boolean m_locked = false;

	/**
	 * Releases lock on mutex.
	 */
	public synchronized void unlock()
	{
		m_locked = false;
		notifyAll();
	}

	/**
	 * Acquire lock on mutex.  Blocks until mutex is unlocked by
	 * another thread.
	 */
	public synchronized void lock()
	{
		while (m_locked)
		{
			try
			{
				wait(0);
			}
			catch (InterruptedException e)
			{
			}
		}
		m_locked = true;
	}
}
