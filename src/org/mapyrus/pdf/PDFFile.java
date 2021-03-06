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

package org.mapyrus.pdf;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;

import org.mapyrus.MapyrusException;
import org.mapyrus.MapyrusMessages;

/**
 * Provides functions for parsing PDF format files.
 */
public class PDFFile
{
	private String m_filename;
	private RandomAccessFile m_pdfFile;
	private HashMap<Integer, PDFObject> m_objects;
	private ArrayList<PDFObject> m_pageObjects;

	public PDFFile(String filename) throws IOException, MapyrusException
	{
		try
		{
			/*
			 * Open file, find and read the 'xref' table at the end of the
			 * file giving the file offset of each object.
			 */
			m_filename = filename;
			m_pdfFile = new RandomAccessFile(filename, "r");
			String header = m_pdfFile.readLine();
			if (!header.startsWith("%PDF-"))
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
					": " + m_filename);
			}
	
			byte []xrefBuf = new byte[20];
			m_pdfFile.seek(m_pdfFile.length() - xrefBuf.length);
			m_pdfFile.readLine();	/* skip line with 'startxref' keyword */
			String line = m_pdfFile.readLine();
			long xrefOffset = Long.parseLong(line);

			HashMap<Integer, Long> objectOffsets = new HashMap<Integer, Long>();
			m_pdfFile.seek(xrefOffset);
			PDFObject trailer = readXrefSection(objectOffsets);

			/*
			 * Read each of the objects in the PDF file. 
			 */
			m_objects = new HashMap<Integer, PDFObject>(objectOffsets.size());
			for (Integer key : objectOffsets.keySet())
			{
				Long objectOffset = objectOffsets.get(key);
				m_pdfFile.seek(objectOffset.longValue());

				int id = readObjectBegin();
				if (id != key.intValue())
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
						": " + m_filename);
				}
				PDFObject obj = readObject();
				long streamOffset = readObjectEnd();
				obj.setStreamOffset(streamOffset);
	
				m_objects.put(key, obj);
			}

			/*
			 * Find root object containing reference to pages.
			 */
			PDFObject rootObject = getDictionaryValue(trailer, "/Root");
			PDFObject pagesObject = getDictionaryValue(rootObject, "/Pages");

			/*
			 * Make list of objects for each page.
			 */
			m_pageObjects = buildPageObjectList(pagesObject);
		}
		catch(IOException e)
		{
			/*
			 * Ensure file is closed on error.
			 */
			close();
			throw(e);
		}
		catch (MapyrusException e)
		{
			close();
			throw(e);
		}
	}

	/**
	 * Build list of pages from object defining page layout.
	 * @param pageObject object for page or pages.
	 * @return list of objects, one for each page.
	 */
	private ArrayList<PDFObject> buildPageObjectList(PDFObject pagesObject)
		throws MapyrusException
	{
		ArrayList<PDFObject> retval = new ArrayList<PDFObject>();
		PDFObject kidsObject = getDictionaryValue(pagesObject, "/Kids");
		PDFObject[] kidsArray = kidsObject.getArray();
		for (int i = 0; i < kidsArray.length; i++)
		{
			PDFObject kidObject = kidsArray[i];
			if (kidObject.isReference())
				kidObject = (PDFObject)m_objects.get(Integer.valueOf(kidObject.getReference()));
			PDFObject objectType = getDictionaryValue(kidObject, "/Type");
			if (objectType.getValue().equals("/Page"))
			{
				retval.add(kidObject);
			}
			else
			{
				retval.addAll(buildPageObjectList(kidObject));
			}
		}
		return(retval);
	}

	/**
	 * Read xref sections from PDF file.
	 * @param objectOffsets table to save offset of each object into.
	 */
	private PDFObject readXrefSection(HashMap<Integer, Long> objectOffsets)
		throws IOException, MapyrusException
	{
		String line = m_pdfFile.readLine();	/* skip line with 'xref' keyword */
		line = m_pdfFile.readLine();

		while (!line.equals("trailer"))
		{
			StringTokenizer st = new StringTokenizer(line);

			if (st.countTokens() < 2)
			{
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
					": " + m_filename);
			}
			int startIndex = Integer.parseInt(st.nextToken());
			int count = Integer.parseInt(st.nextToken());
			
			for (int i = 0; i < count; i++)
			{
				line = m_pdfFile.readLine();
				st = new StringTokenizer(line);
				if (st.countTokens() < 3)
				{
					throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
						": " + m_filename);
				}
				Long objOffset = Long.valueOf(st.nextToken());
				st.nextToken();	/* skip generation number */
				String status = st.nextToken();
				if (status.equals("n"))
				{
					/*
					 * Newest offsets were added first.
					 * Do not overwrite them with older values from
					 * previous 'xref' sections.
					 */
					Integer key = Integer.valueOf(i + startIndex);
					if (!objectOffsets.containsKey(key))
						objectOffsets.put(key, objOffset);
				}
			}
			line = m_pdfFile.readLine();
		}

		/*
		 * Now read the trailer dictionary.
		 */
		PDFObject trailer = readObject();
		PDFObject prevObject = getDictionaryValue(trailer, "/Prev");
		if (prevObject != null)
		{
			/*
			 * Read any previous xref section in the PDF file too.
			 */
			String s = prevObject.getValue();
			long offset = Long.parseLong(s);
			m_pdfFile.seek(offset);
			readXrefSection(objectOffsets);
		}
		return(trailer);
	}

	/**
	 * Close the PDF file.
	 */
	public void close()
	{
		if (m_pdfFile != null)
		{
			try
			{
				m_pdfFile.close();
			}
			catch (IOException e)
			{
			}
			m_pdfFile = null;
		}
	}

	/**
	 * Get number of pages in PDF file.
	 * @return page count.
	 */
	public int getPageCount()
	{
		return(m_pageObjects.size());
	}

	/**
	 * Get resources used by a page.
	 * @param page page number.
	 * @return resources used by page. 
	 */
	private PDFObject getResources(int page) throws MapyrusException
	{
		PDFObject pageObject = m_pageObjects.get(page - 1);
		PDFObject resourcesObject = getDictionaryValue(pageObject, "/Resources");
		return(resourcesObject);
	}

	/**
	 * Get page contents.
	 * @param page page number.
	 * @return page contents.
	 */
	public byte[] getContents(int page) throws IOException, MapyrusException
	{
		byte[] retval = null;
		PDFObject pageObject = m_pageObjects.get(page - 1);
		PDFObject contentsObject = getDictionaryValue(pageObject, "/Contents");
		if (contentsObject != null)
		{
			resolveAllReferences(contentsObject);
			if (contentsObject.isArray())
			{
				/*
				 * Build single buffer from all contents.
				 */
				PDFObject[] objs = contentsObject.getArray();
				byte[][]buf = new byte[objs.length][];
				int totalLength = 0;
				for (int i = 0; i < objs.length; i++)
				{
					buf[i] = objs[i].getStream(m_pdfFile, m_filename, m_objects);
					totalLength += buf[i].length;
				}
				retval = new byte[totalLength];
				int index = 0;
				for (int i = 0; i < buf.length; i++)
				{
					System.arraycopy(buf[i], 0, retval, index, buf[i].length);
					index += buf[i].length;
				}
			}
			else
			{
				retval = contentsObject.getStream(m_pdfFile, m_filename, m_objects);
			}
		}
		return(retval);
	}

	/**
	 * Get resources for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @param dictKey key of values to fetch from resources dictionary.
	 * @returnlist of PDF objects containing for this key.
	 */
	private ArrayList<StringBuffer> getResource(int page, int objectNumber, String dictKey)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = null;
		PDFObject resourcesObject = getResources(page);
		PDFObject obj = getDictionaryValue(resourcesObject, dictKey);
		if (obj != null)
		{
			retval = obj.toPDFString(objectNumber, false, false, m_objects, m_pdfFile, m_filename);
		}
		return(retval);
	}

	/**
	 * Get external graphics states for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing graphics states.
	 */
	public ArrayList<StringBuffer> getExtGState(int page, int objectNumber) throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/ExtGState");
		return(retval);
	}

	/**
	 * Get color states for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing color spaces.
	 */
	public ArrayList<StringBuffer> getColorSpace(int page, int objectNumber)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/ColorSpace");
		return(retval);
	}

	/**
	 * Get patterns for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing patterns.
	 */
	public ArrayList<StringBuffer> getPattern(int page, int objectNumber)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/Pattern");
		return(retval);
	}

	/**
	 * Get shading for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing shading.
	 */
	public ArrayList<StringBuffer> getShading(int page, int objectNumber)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/Shading");
		return(retval);
	}

	/**
	 * Get fonts for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing fonts.
	 */
	public ArrayList<StringBuffer> getFont(int page, int objectNumber)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/Font");
		return(retval);
	}

	/**
	 * Get external objects for page.
	 * @param page page number.
	 * @param objectNumber object number to use for first PDF object.
	 * @return list of PDF objects containing external objects.
	 */
	public ArrayList<StringBuffer> getXObject(int page, int objectNumber)
		throws IOException, MapyrusException
	{
		ArrayList<StringBuffer> retval = getResource(page, objectNumber, "/XObject");
		return(retval);
	}

	/**
	 * Get media box for page.
	 * @param page page number.
	 * @return (x1, y1) and (x2, y2) coordinates of page in points.
	 */
	public int[] getMediaBox(int page) throws MapyrusException
	{
		PDFObject pageObject = m_pageObjects.get(page - 1);
		PDFObject boxObject = getDictionaryValue(pageObject, "/MediaBox");
		PDFObject[] boxArray = boxObject.getArray();
		int retval[] = new int[4];
		for (int i = 0; i < retval.length; i++)
		{
			PDFObject obj = boxArray[i];
			if (obj.isReference())
				obj = (PDFObject)m_objects.get(Integer.valueOf(obj.getReference()));
			String s = obj.getValue();
			retval[i] = (int)Math.round(Double.parseDouble(s));
		}
		return(retval);
	}

	/**
	 * Read next character from PDF file, skipping over comments.
	 * @param skipComments true if comments are to be skipped.
	 * @return next char, ignoring comments.
	 */
	private int readChar(boolean skipComments) throws IOException, MapyrusException
	{
		int c = m_pdfFile.read();
		if (c == -1)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.UNEXPECTED_EOF) +
				": " + m_filename);
		}
		else if (skipComments && c == '%')
		{
			c = readChar(skipComments);
			while (c != '\r' && c != '\n')
				c = readChar(skipComments);
		}
		return(c);
	}

	/**
	 * Peek next character from PDF file without reading it, skipping over comments.
	 * @param skipComments true if comments are to be skipped.
	 * @return next char, ignoring comments.
	 */
	private int peekChar(boolean skipComments) throws IOException, MapyrusException
	{
		long offset = m_pdfFile.getFilePointer();
		int c = readChar(skipComments);
		m_pdfFile.seek(offset);
		return(c);
	}

	/**
	 * Read object from current position in PDF file.
	 * @return a String or HashMap of key, value dictionary pairs.
	 */
	private PDFObject readObject() throws IOException, MapyrusException
	{
		PDFObject retval = null;
		int c, lastC = -1;
		StringBuffer sb = new StringBuffer();

		/*
		 * Skip whitespace.
		 */
		c = readChar(true);
		while (Character.isWhitespace((char)c))
			c = readChar(true);

		if (c == '(')
		{
			/*
			 * Parse simple string.
			 */
			sb.append((char)c);
			do
			{
				lastC = c;
				c = readChar(false);
				sb.append((char)c);
			}	
			while (!(c == ')' && lastC != '\\'));
			retval = new PDFObject(sb.toString());
		}
		else if (c == '<')
		{
			c = peekChar(true);
			if (c != '<')
			{
				/*
				 * Parse simple hex string.
				 */
				sb.append('<');
				do
				{
					lastC = c;
					c = readChar(false);
					sb.append((char)c);
				}
				while (!(c == '>' && lastC != '\\'));
				retval = new PDFObject(sb.toString());
			}
			else
			{
				readChar(true);

				/*
				 * Parse dictionary.
				 */
				HashMap<String, PDFObject> dictionary = new HashMap<String, PDFObject>();

				/*
				 * Skip whitespace.
				 */
				c = readChar(true);
				while (Character.isWhitespace((char)c))
					c = readChar(true);
				while (c == '/')
				{
					sb = new StringBuffer();
					sb.append((char)c);
					c = peekChar(false);
					while ((!Character.isWhitespace((char)c)) &&
						c != '[' && c != '/' && c != '(' && c != '<')
					{
						readChar(false);
						sb.append((char)c);
						c = peekChar(false);
					}
					PDFObject value = readObject();
					dictionary.put(sb.toString(), value);

					/*
					 * Skip whitespace.
					 */
					c = readChar(true);
					while (Character.isWhitespace((char)c))
						c = readChar(true);
				}
				c = readChar(true);	/* skip over second '>' in name */
				retval = new PDFObject(dictionary);
			}
		}
		else if (c == '[')
		{
			/*
			 * Parse array of objects.
			 */
			ArrayList<PDFObject> list = new ArrayList<PDFObject>();

			PDFObject obj = readObject();
			while (obj != null)
			{
				list.add(obj);
				obj = readObject();
			}
			PDFObject[] array = new PDFObject[list.size()];
			list.toArray(array);
			retval = new PDFObject(array);
		}
		else if (c == ']')
		{
			/*
			 * End of array.
			 */
			retval = null;
		}
		else
		{
			/*
			 * Parse number or identifier or reference.
			 */
			sb = new StringBuffer();
			sb.append((char)c);
			c = peekChar(false);
			while ((!Character.isWhitespace((char)c)) &&
				c != '/' && c != ']' && c != '>')
			{
				readChar(false);
				sb.append((char)c);
				c = peekChar(false);
			}
			long offset = m_pdfFile.getFilePointer();
			
			/*
			 * Check if this is a reference of the form '12 0 R'.
			 * Check for any whitespace, then a '0', more whitespace, then a 'R'.
			 */
			c = readChar(true);
			while (Character.isWhitespace((char)c))
				c = readChar(true);
			if (c == '0')
			{
				c = readChar(true);
				while (Character.isWhitespace((char)c))
					c = readChar(true);
				if (c == 'R')
				{
					int ref = Integer.parseInt(sb.toString());
					retval = new PDFObject(ref);
				}
				else
				{
					retval = new PDFObject(sb.toString());
					m_pdfFile.seek(offset);
				}
			}
			else
			{
				retval = new PDFObject(sb.toString());
				m_pdfFile.seek(offset);
			}
		}
		return(retval);
	}

	/**
	 * Read object header from current position in PDF file.
	 * @return object number.
	 */
	private int readObjectBegin() throws IOException, MapyrusException
	{
		/*
		 * Read object header with format '17 0 obj' from PDF file.
		 */
		int id;
		StringBuffer sb = new StringBuffer();
		int c = readChar(true);
		while (!Character.isWhitespace((char)c))
		{
			sb.append((char)c);
			c = readChar(true);
		}
		try
		{
			id = Integer.parseInt(sb.toString());
		}
		catch (NumberFormatException e)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
				": " + m_filename + ": " + sb.toString());
		}

		for (int i = 0; i < 2; i++)
		{
			while (Character.isWhitespace((char)c))
				c = readChar(true);

			c = peekChar(true);
			while ((!Character.isWhitespace((char)c)) && c != '<' && c != '[')
			{
				readChar(true);
				c = peekChar(true);
			}
		}
		return(id);
	}

	/**
	 * Read keywords marking the stream for object.
	 * @return file offset of stream for this object.
	 */
	private long readObjectEnd() throws IOException, MapyrusException
	{
		StringBuffer sb = new StringBuffer();
		
		/*
		 * Skip whitespace.
		 */
		int c = readChar(true);
		while (Character.isWhitespace((char)c))
			c = readChar(true);

		/*
		 * Read 'stream' or 'endobj' keyword.
		 */
		while (!Character.isWhitespace((char)c))
		{
			sb.append((char)c);
			c = readChar(true);
		}
		if (c == '\r')
			readChar(false);
		long retval = -1;
		if (sb.toString().equals("stream"))
			retval = m_pdfFile.getFilePointer();
		return(retval);
	}

	/**
	 * Lookup value in dictionary.
	 * @param obj dictionary object.
	 * @param key key to lookup in dictionary.
	 * @return value of key.
	 */
	private PDFObject getDictionaryValue(PDFObject dictObj, String key)
		throws MapyrusException
	{
		HashMap<String, PDFObject> dict = dictObj.getDictionary();

		if (dict == null)
		{
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.FAILED_PDF) +
				": " + m_filename);
		}
		PDFObject value = (PDFObject)dict.get(key);
		if (value != null && value.isReference())
		{
			value = (PDFObject)m_objects.get(Integer.valueOf(value.getReference()));
		}
		return(value);
	}

	/**
	 * Replace all references in an object by their actual values.
	 * @param obj object in which to replace values.
	 */
	private void resolveAllReferences(PDFObject obj)
		throws IOException, MapyrusException
	{
		if (obj.isDictionary())
		{
			Set<String> keys = obj.getDictionary().keySet();
			for (String key : keys)
			{
				PDFObject value = obj.getDictionary().get(key);
				resolveAllReferences(value);
			}
		}
		else if (obj.isArray())
		{
			PDFObject[] arrayObjects = obj.getArray();
			for (int i = 0; i < arrayObjects.length; i++)
				resolveAllReferences(arrayObjects[i]);
		}
		else if (obj.isReference())
		{
			PDFObject value = (PDFObject)m_objects.get(Integer.valueOf(obj.getReference()));
			resolveAllReferences(value);
			obj.setValue(value);
		}
	}

	public String getFilename()
	{
		return(m_filename);
	}

	public static void main(String []args)
	{
		try
		{
			PDFFile pdf = new PDFFile("/tmp/text1.pdf");
			int nPages = pdf.getPageCount();
			for (int i = 1; i <= nPages; i++)
			{
				System.out.println("-- Page " + i);
				int[] box = pdf.getMediaBox(i);
				System.out.println("[" + box[0] + " " + box[1] + " " + box[2] + " " + box[3] + "]");
				System.out.println("-- ExtGState");
				ArrayList<StringBuffer> objects = pdf.getExtGState(i, 300);
				for (int j = 0; j < objects.size(); j++)
					System.out.println(objects.get(j));
				System.out.println("-- XObject");
				objects = pdf.getXObject(i, 400);
				if (objects != null)
				{
					for (int j = 0; j < objects.size(); j++)
						System.out.println(objects.get(j));
				}
				System.out.println("-- Contents");
				byte[] contents = pdf.getContents(i);
				System.out.println("contents.length=" + contents.length);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
