/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 - 2009 Simon Chenery.
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

package org.mapyrus.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.mapyrus.MapyrusMessages;

/**
 * Menu bar for Mapyrus GUI.
 */
public class MapyrusMenuBar extends JMenuBar
{
	static final long serialVersionUID = 0x3302;
	private ArrayList<MapyrusEventListener> m_menuBarListeners = new ArrayList<MapyrusEventListener>();

	/**
	 * Create menu bar used in Mapyrus Viewer.
	 */
	public MapyrusMenuBar()
	{
		JMenu fileMenu = new JMenu(MapyrusMessages.get(MapyrusMessages.FILE));
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem newTabItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.NEW_TAB));
		newTabItem.setMnemonic(KeyEvent.VK_N);
		fileMenu.add(newTabItem);
		newTabItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.NEW_TAB_ACTION);
			}
		});

		JMenuItem openFileItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.OPEN_FILE));
		openFileItem.setMnemonic(KeyEvent.VK_O);
		fileMenu.add(openFileItem);
		openFileItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.OPEN_FILE);
			}
		});

		JMenuItem closeTabItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.CLOSE_TAB));
		closeTabItem.setMnemonic(KeyEvent.VK_C);
		fileMenu.add(closeTabItem);
		closeTabItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.CLOSE_TAB);
			}
		});

		JMenuItem saveTabItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.SAVE_TAB));
		saveTabItem.setMnemonic(KeyEvent.VK_S);
		fileMenu.add(saveTabItem);
		saveTabItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.SAVE_TAB);
			}
		});

		JMenuItem exitItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.EXIT));
		exitItem.setMnemonic(KeyEvent.VK_X);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);
		exitItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.EXIT);
			}
		});

		JMenu editMenu = new JMenu(MapyrusMessages.get(MapyrusMessages.EDIT));
		editMenu.setMnemonic(KeyEvent.VK_E);

		JMenuItem copyItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.COPY));
		copyItem.setMnemonic(KeyEvent.VK_C);
		copyItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.COPY);
			}
		});
		editMenu.add(copyItem);

		JMenuItem pngExportItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.EXPORT_AS_PNG));
		pngExportItem.setMnemonic(KeyEvent.VK_E);
		editMenu.add(pngExportItem);
		pngExportItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.EXPORT_PNG);
			}
		});

		JMenuItem pdfExportItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.EXPORT_AS_PDF));
		pdfExportItem.setMnemonic(KeyEvent.VK_F);
		editMenu.add(pdfExportItem);
		pdfExportItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.EXPORT_PDF);
			}
		});

		JMenu helpMenu = new JMenu(MapyrusMessages.get(MapyrusMessages.HELP));
		helpMenu.setMnemonic(KeyEvent.VK_H);

		JMenuItem onlineHelpItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.ONLINE_HELP));
		onlineHelpItem.setMnemonic(KeyEvent.VK_H);
		onlineHelpItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.ONLINE_HELP);
			}
		});
		helpMenu.add(onlineHelpItem);

		JMenuItem aboutItem = new JMenuItem(MapyrusMessages.get(MapyrusMessages.ABOUT));
		aboutItem.setMnemonic(KeyEvent.VK_A);
		aboutItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				fireEvent(MapyrusEventListener.Action.ABOUT);
			}
		});
		helpMenu.add(aboutItem);

		add(fileMenu);
		add(editMenu);
		add(helpMenu);
	}

	public void addListener(MapyrusEventListener listener)
	{
		m_menuBarListeners.add(listener);
	}

	private void fireEvent(MapyrusEventListener.Action action)
	{
		for (MapyrusEventListener listener : m_menuBarListeners)
		{
			listener.actionPerformed(action);
		}
	}
}
