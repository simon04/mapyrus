/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003, 2004, 2005 Simon Chenery.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * @(#) $Id$
 */
package org.mapyrus;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.ScrollPane;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

/**
 * A window displaying an image created by Mapyrus.
 */
public class MapyrusFrame
{
	private Mutex mMutex;
	JFrame mFrame;
	BufferedImage mImage;

	/**
	 * Create new window displaying image.
	 * @param title tile for window.
	 * @param image image to display in window.
	 */
	public MapyrusFrame(String title, BufferedImage image)
	{
		mImage = image;
		mFrame = new JFrame(title);

		ImageIcon icon = new ImageIcon(image);
		final JLabel label = new JLabel(icon);

		Container contentPane = mFrame.getContentPane();
		contentPane.setLayout(new BorderLayout());

		/*
		 * Put image on an icon, putting it in a scrollable area if it is bigger
		 * than the screen.
		 */
		double screenWidth = Constants.getScreenWidth() / Constants.MM_PER_INCH *
			Constants.getScreenResolution();
		double screenHeight = Constants.getScreenHeight() / Constants.MM_PER_INCH *
			Constants.getScreenResolution();
		if (image.getWidth() > screenWidth || image.getHeight() > screenHeight)
		{
			ScrollPane pane = new ScrollPane();
			pane.add(label);
			contentPane.add(pane, BorderLayout.CENTER);
		}
		else
		{
			contentPane.add(label, BorderLayout.CENTER);
		}

		mMutex = new Mutex();
		mMutex.lock();

		JMenuBar menubar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem pngExportItem = new JMenuItem("Export as PNG");
		pngExportItem.setMnemonic(KeyEvent.VK_E);
		fileMenu.add(pngExportItem);
		pngExportItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				export("png");
			}
		});

		JMenuItem printItem = new JMenuItem("Print");
		printItem.setMnemonic(KeyEvent.VK_P);
		fileMenu.addSeparator();
		fileMenu.add(printItem);
		printItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				print();
			}
		});

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.setMnemonic(KeyEvent.VK_X);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);
		exitItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				mMutex.unlock();
			}
		});

		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);
		JMenuItem copyItem = new JMenuItem("Copy");
		copyItem.setMnemonic(KeyEvent.VK_C);
		copyItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				ImageSelection imageSelection = new ImageSelection(mImage);
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(imageSelection, null);
			}
		});
		editMenu.add(copyItem);

		menubar.add(fileMenu);
		menubar.add(editMenu);
		contentPane.add(menubar, BorderLayout.NORTH);

		mFrame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				mMutex.unlock();
			}
		});

		mFrame.pack();
		mFrame.setVisible(true);
	}

	/**
	 * Print image.
	 */
	private void print()
	{
		PrintService defaultPrintService = PrintServiceLookup.lookupDefaultPrintService();

		if (defaultPrintService == null)
		{
			JOptionPane.showMessageDialog(mFrame,
				MapyrusMessages.get(MapyrusMessages.NO_DEFAULT_PRINTER),
				"Print Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try
		{
			DocPrintJob pj = defaultPrintService.createPrintJob();
			DocFlavor flavor = new DocFlavor("application/x-java-jvm-local-objectref",
				"java.awt.image.renderable.RenderableImage");
			HashDocAttributeSet attribSet = new HashDocAttributeSet();
			SimpleDoc doc = new SimpleDoc(mImage, flavor, attribSet);

			HashPrintRequestAttributeSet attribs = new HashPrintRequestAttributeSet();
			pj.print(doc, attribs);
		}
		catch (PrintException e)
		{
			JOptionPane.showMessageDialog(mFrame, e.getMessage(),
				"Print Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Export image to a file.
	 * @param format file format to export to.
	 */
	private void export(String format)
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setMultiSelectionEnabled(false);
		int retval = fileChooser.showSaveDialog(mFrame);
		if (retval == JFileChooser.APPROVE_OPTION)
		{
			try
			{
				File selectedFile = fileChooser.getSelectedFile();
				ImageIO.write(mImage, format, selectedFile);
			}
			catch (IOException e)
			{
				System.err.println(e.getMessage());
			}
		}
	}

	/**
	 * Block until window is closed.
	 */
	public void waitForClose()
	{
		/*
		 * Wait for window to be closed and lock to be released.
		 */
		mMutex.lock();
	}
}
