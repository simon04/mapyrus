/*
 * This file is part of Mapyrus, software for plotting maps.
 * Copyright (C) 2003 Simon Chenery.
 *
 * Mapyrus is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mapyrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mapyrus; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * @(#) $Id$
 */
package org.mapyrus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.ImageIcon;

import org.mapyrus.dataset.GeographicDataset;

/**
 * Maintains state information during interpretation inside a single procedure block. 
 * Holds the graphics attributes (color, line styles, transformations, etc.), the 
 * variables set by the user and connections to external data sources.
 */
public class Context
{
	/*
	 * Units of world coordinate system.
	 */
	public static final int WORLD_UNITS_METRES = 1;
	public static final int WORLD_UNITS_FEET = 2;
	public static final int WORLD_UNITS_DEGREES = 3;

	/*
	 * Bit flags of graphical attributes.  OR-ed together
	 * to control which attributes have been set, or not set.
	 */
	private static final int ATTRIBUTE_FONT = 1;
	private static final int ATTRIBUTE_JUSTIFY = 2;
	private static final int ATTRIBUTE_COLOR = 4;
	private static final int ATTRIBUTE_LINESTYLE = 8;
	private static final int ATTRIBUTE_CLIP = 16;

	/*
	 * Projection transformation may results in some strange warping.
	 * To get a better estimate of extents when projecting to world coordinate
	 * system we project many points in a grid to find minimum and maximum values.
	 */
	private static final int PROJECTED_GRID_STEPS = 5;

	/*
	 * Page resolution to use when no output page defined.
	 */
	private static final int DEFAULT_RESOLUTION = 96;

	/*
	 * Fixed miter limit for line joins.
	 */
	private static final float MITER_LIMIT = 10.0f;

	/*
	 * Graphical attributes
	 */	
	private Color mColor;
	private BasicStroke mLinestyle;
	private int mJustify;
	private String mFontName;
	private double mFontSize;
	private double mFontRotation;

	/*
	 * Bit flags of graphical attributes that have been changed by caller
	 * but not set yet.
	 */
	private int mAttributesPending;

	/*
	 * Bit flags of graphical attributes that have been changed in this context.
	 */
	private int mAttributesChanged;
	
	/*
	 * Transformation matrix and cumulative scaling factors and rotation.
	 */
	private AffineTransform mCtm;
	private double mScaling;
	private double mRotation;

	/*
	 * Projection transformation from one world coordinate system to another.
	 */
	private WorldCoordinateTransform mProjectionTransform;

	/*
	 * Transformation matrix from world coordinates to page coordinates
	 * and the units of world coordinates.
	 */
	private AffineTransform mWorldCtm;
	private Rectangle2D.Double mWorldExtents;
	private int mWorldUnits;
	
	/*
	 * Coordinates making up path.
	 */
	private GeometricPath mPath;

	/*
	 * Path in context from which this context was created.
	 * Used when path is not modified in this context to avoid
	 * needlessly copying paths from one context to another.
	 */
	private GeometricPath mExistingPath;

	/*
	 * List of clip polygons making up the complete clipping path.
	 * We need a list of clip polygons instead of a single clip
	 * polygon to preserve the inside/outside of each individual
	 * clip polygon.
	 */
	private ArrayList mClippingPaths;
	
	/*
	 * Currently defined variables and variables that are local
	 * to this context.
	 */
	private HashMap mVars;
	private HashSet mLocalVars;

	/*
	 * Output device we are drawing to.
	 */
	private OutputFormat mOutputFormat;
	
	/*
	 * Flag true if output and dataset defined in this context.  In this case
	 * we must close the output file and dataset when this context is finished.
	 */
	private boolean mOutputDefined;
	private boolean mDatasetDefined;

	/*
	 * Dataset currently being read from, the next row to provide to caller
	 * and the number of rows already fetched from it.
	 */
	private Dataset mDataset;

	/*
	 * Name of procedure block that this context is executing in. 
	 */
	private String mBlockName;

	/**
	 * Clear graphics context to empty state.
	 * @param c context to clear.
	 */
	private void initialiseContext(Context c)
	{
		mColor = Color.BLACK;
		mLinestyle = new BasicStroke(0.1f);
		mJustify = OutputFormat.JUSTIFY_LEFT | OutputFormat.JUSTIFY_BOTTOM;
		mFontName = "SansSerif";
		mFontSize = 5;
		mFontRotation = 0;

		mPath = mExistingPath = null;
		mClippingPaths = null;
		mCtm = new AffineTransform();
		mScaling =  1.0;
		mRotation = 0.0;

		mAttributesPending = (ATTRIBUTE_CLIP | ATTRIBUTE_COLOR |
			ATTRIBUTE_FONT | ATTRIBUTE_JUSTIFY | ATTRIBUTE_LINESTYLE);
		mAttributesChanged = 0;
		
		mProjectionTransform = null;
		mWorldCtm = null;
	}

	/**
	 * Create a new context with reasonable default values.
	 */		
	public Context()
	{
		mVars = null;
		mLocalVars = null;

		mOutputFormat = null;
		mOutputDefined = false;
		mDatasetDefined = false;
		mDataset = null;
		
		/*
		 * First context is outside of any procedure block.
		 */
		mBlockName = null;

		initialiseContext(this);
	}

	/**
	 * Create a new context, making a copy from an existing context.
	 * @param existing is context to copy from.
	 * @param blockName is name of procedure block which context will execute.
	 */
	public Context(Context existing, String blockName)
	{
		mColor = existing.mColor;
		mLinestyle = existing.mLinestyle;
		mJustify = existing.mJustify;
		mFontName = existing.mFontName;
		mFontSize = existing.mFontSize;
		mFontRotation = existing.mFontRotation;

		mCtm = new AffineTransform(existing.mCtm);
		mProjectionTransform = null;
		mWorldCtm = null;
		mScaling = existing.mScaling;
		mRotation = existing.mRotation;
		mDataset = existing.mDataset;

		/*
		 * Only create variable lookup tables when some values are
		 * defined locally.
		 */
		mVars = null;
		mLocalVars = null;

		/*
		 * Don't copy path -- it can be large.
		 * Just keep reference to existing path.
		 * 
		 * Create path locally when needed.  If path is referenced without
		 * being created then we can reuse path from existing context instead.
		 * 
		 * This saves unnecessary copying of paths when contexts are created.
		 */
		mPath = null;
		if (existing.mPath != null)
			mExistingPath = existing.mPath;
		else
			mExistingPath = existing.mExistingPath;

		/*
		 * Copy list of paths we must clip against.
		 */
		if (existing.mClippingPaths != null)			
			mClippingPaths = (ArrayList)(existing.mClippingPaths.clone());
		else
			mClippingPaths = null;

		mOutputFormat = existing.mOutputFormat;
		mOutputDefined = false;

		/*
		 * Save state in parent context so it won't be disturbed by anything
		 * that gets changed in this new context.
		 */		
		if (mOutputFormat != null)
		{
			mOutputFormat.saveState();
		}
		mDatasetDefined = false;

		mAttributesPending = existing.mAttributesPending;
		mAttributesChanged = 0;
		
		mBlockName = blockName;
	}

	private GeometricPath getDefinedPath()
	{
		GeometricPath retval;
		
		/*
		 * Return path defined in this context, or one defined
		 * in previous context if nothing set here.
		 */
		if (mPath != null)
			retval = mPath;
		else
			retval = mExistingPath;
		return(retval);
	}

	/**
	 * Return page width for output we are currently writing.
	 * @return width in millimetres.
	 */
	public double getPageWidth()
	{
		double retval;
		
		if (mOutputFormat == null)
			retval = 0.0;
		else
			retval = mOutputFormat.getPageWidth();
		
		return(retval);
	}

	/**
	 * Return page height for output we are currently writing.
	 * @return height in millimetres.
	 */
	public double getPageHeight()
	{
		double retval;
		
		if (mOutputFormat == null)
			retval = 0.0;
		else
			retval = mOutputFormat.getPageHeight();
		
		return(retval);
	}
	
	/**
	 * Return file format for output we are currently writing.
	 * @return file format.
	 */
	public String getPageFormat()
	{
		String retval;
		
		if (mOutputFormat == null)
			retval = "";
		else
			retval = mOutputFormat.getPageFormat();
		
		return(retval);
	}

	/**
	 * Return resolution of page we are writing to as a distance measurement.
	 * @return distance in millimetres between centres of adjacent pixels.
	 */
	public double getResolution() throws MapyrusException
	{
		double retval;
		
		if (mOutputFormat == null)
			retval = Constants.MM_PER_INCH / DEFAULT_RESOLUTION;
		else
			retval = mOutputFormat.getResolution();

		return(retval);
	}

	/**
	 * Set graphics attributes (color, line width, etc.) if they
	 * have changed since the last time we drew something.
	 * @param attributeMask bit mask of attributes to set: ATTRIBUTE_*.
	 */
	private void setGraphicsAttributes(int attributeMask)
		throws IOException, MapyrusException
	{
		int maskComplement = (~attributeMask);

		if ((mAttributesPending & ATTRIBUTE_FONT & attributeMask) != 0)
			mOutputFormat.setFontAttribute(mFontName, mFontSize, mFontRotation);
		if ((mAttributesPending & ATTRIBUTE_JUSTIFY & attributeMask) != 0)
			mOutputFormat.setJustifyAttribute(mJustify);
		if ((mAttributesPending & ATTRIBUTE_COLOR & attributeMask) != 0)
			mOutputFormat.setColorAttribute(mColor);
		if ((mAttributesPending & ATTRIBUTE_LINESTYLE & attributeMask) != 0)
			mOutputFormat.setLinestyleAttribute(mLinestyle);
		if ((mAttributesPending & ATTRIBUTE_CLIP & attributeMask) != 0)
			mOutputFormat.setClipAttribute(mClippingPaths);

		/*
		 * Clear attributes we've just set -- they are no longer pending.
		 */
		mAttributesPending = (mAttributesPending & maskComplement);
	}

	/**
	 * Flag that graphics attributes have been changed by a call to a procedure.
	 * @param attributes bit flags of attributes that are changed.
	 */
	public void setAttributesChanged(int attributes)
	{
		mAttributesPending |= attributes;
		mAttributesChanged |= attributes;
	}

	/**
	 * Sets output file for drawing to.
	 * @param filename name of image file output will be saved to.
	 * @param format is image format for saving output.
	 * @param width is the page width (in mm).
	 * @param height is the page height (in mm).
	 * @param extras contains extra settings for this output.
	 * @param stdoutStream standard output stream for program.
	 */
	public void setOutputFormat(String format, String filename,
		int width, int height, String extras,
		PrintStream stdoutStream)
		throws IOException, MapyrusException
	{
		if (mOutputDefined && mOutputFormat != null)
		{
			/*
			 * Finish any previous page before beginning a new one.
			 */
			mOutputFormat.closeOutputFormat();
		}

		/*
		 * Clear graphics context before beginning new page.  
		 */
		initialiseContext(this);

		mOutputFormat = new OutputFormat(filename, format,
			width, height, extras, stdoutStream);
		mOutputDefined = true;
	}

	/**
	 * Closes a context.  Any output started in this context is completed,
	 * memory used for context is released.
	 * A context cannot be used again after this call.
	 * @return bit flag of graphical attributes that were changed in this context
	 * and cannot be restored.
	 */
	public int closeContext() throws IOException, MapyrusException
	{
		boolean restoredState;

		if (mOutputFormat != null && !mOutputDefined)
		{
			/*
			 * If state could be restored then no need for caller to set
			 * graphical attributes back to their old values again.
			 */
			restoredState = mOutputFormat.restoreState();
			if (restoredState)
				mAttributesChanged = 0;
		}

		if (mOutputDefined)
		{
			mOutputFormat.closeOutputFormat();
			mOutputFormat = null;
			mOutputDefined = false;
		}

		/*
		 * Close any dataset we opened in this context.
		 */
		if (mDatasetDefined)
		{
			mDataset.close();
		}

		mDataset = null;
		mPath = mExistingPath = null;
		mClippingPaths = null;
		mVars = null;
		mLocalVars = null;
		return(mAttributesChanged);
	}

	/**
	 * Sets linestyle.
	 * @param width is width for lines in millimetres.
	 * @param cap is a BasicStroke end cap value.
	 * @param join is a BasicStroke line join value.
	 * @param phase is offset at which pattern is started.
	 * @param dashes list of dash pattern lengths.
	 */
	public void setLinestyle(double width, int cap, int join,
		double phase, float []dashes)
	{
		/*
		 * Adjust width and dashes by current scaling factor.
		 */
		if (dashes == null)
		{
			mLinestyle = new BasicStroke((float)(width * mScaling),
				cap, join, MITER_LIMIT);
		}
		else
		{
			for (int i = 0; i < dashes.length; i++)
				dashes[i] *= mScaling;

			mLinestyle = new BasicStroke((float)(width * mScaling), cap, join,
				MITER_LIMIT, dashes, (float)phase);
		}
		mAttributesPending |= ATTRIBUTE_LINESTYLE;
		mAttributesChanged |= ATTRIBUTE_LINESTYLE;
	}

	/**
	 * Sets color.
	 * @param c is new color for drawing.
	 */
	public void setColor(Color color)
	{
		mColor = color;
		mAttributesPending |= ATTRIBUTE_COLOR;
		mAttributesChanged |= ATTRIBUTE_COLOR;
	}

	/**
	 * Sets font for labelling with.
	 * @param fontName is name of font as defined in java.awt.Font class.
	 * @param fontSize is size for labelling in millimetres.
	 */
	public void setFont(String fontName, double fontSize)
	{
		mFontName = fontName;
		mFontSize = fontSize * mScaling;
		mFontRotation = mRotation;
		mAttributesChanged |= ATTRIBUTE_FONT;
		mAttributesPending |= ATTRIBUTE_FONT;
	}

	/**
	 * Sets horizontal and vertical justification for labelling.
	 * @param code is bit flags of JUSTIFY_* constant values for justification.
	 */
	public void setJustify(int code)
	{
		mJustify = code;
		mAttributesChanged |= ATTRIBUTE_JUSTIFY;
		mAttributesPending |= ATTRIBUTE_JUSTIFY;
	}

	/**
	 * Sets scaling for subsequent coordinates.
	 * @param factor is new scaling in X and Y axes.
	 */
	public void setScaling(double factor)
	{
		mCtm.scale(factor, factor);
		mScaling *= factor;
	}
	
	/**
	 * Sets translation for subsequent coordinates.
	 * @param x is new point for origin on X axis.
	 * @param y is new point for origin on Y axis.
	 */
	public void setTranslation(double x, double y)
	{
		mCtm.translate(x, y);
	}
	
	/**
	 * Sets rotation for subsequent coordinates.
	 * @param angle is rotation angle in radians, measured counter-clockwise.
	 */
	public void setRotation(double angle)
	{
		mCtm.rotate(angle);
		mRotation += angle;
		mRotation = Math.IEEEremainder(mRotation, Math.PI * 2);
	}

	/**
	 * Sets transformation from real world coordinates to page coordinates.
	 * @param x1 minimum X world coordinate.
	 * @param y1 minimum Y world coordinate.
	 * @param x2 maximum X world coordinate.
	 * @param y2 maximum Y world coordinate.
	 * @param units units of world coordinates (WORLD_UNITS_METRES,WORLD_UNITS_FEET, etc.)
	 */
	public void setWorlds(double x1, double y1, double x2, double y2, int units)
		throws MapyrusException
	{
		double xDiff = x2 - x1;
		double yDiff = y2 - y1;
		double xMid, yMid;
		double worldAspectRatio = yDiff / xDiff;
		double pageAspectRatio;

		if (mOutputFormat == null)
			throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_OUTPUT));		
		
		pageAspectRatio = mOutputFormat.getPageHeight() / mOutputFormat.getPageWidth();

		/*
		 * Expand world coordinate range in either X or Y axis so
		 * it has same aspect ratio as page.
		 */
		if (worldAspectRatio > pageAspectRatio)
		{
			/*
			 * World coordinate range is taller than page coordinate
			 * system.  Expand X axis range to compensate:
			 * 
			 *  PAGE    WORLDS    EXPANDED WORLDS
			 *  +---+   +---+     +-+---+-+
			 *  |   |   |   |     |<|   |>|
			 * 	|___|   |   |  => |<|   |>|
			 *          |   |     |<|   |>|
			 *          +---+     +-+---+-+
			 */
			xMid = (x1 + x2) / 2.0;
			x1 = xMid - (xDiff / 2.0) * (worldAspectRatio / pageAspectRatio);
			x2 = xMid + (xDiff / 2.0) * (worldAspectRatio / pageAspectRatio);
		}
		else if (worldAspectRatio < pageAspectRatio)
		{
			/*
			 * World coordinate range is wider than page coordinate system.
			 * Expand Y axis range.
			 */
			yMid = (y1 + y2) / 2.0;
			y1 = yMid - (yDiff / 2.0) * (pageAspectRatio / worldAspectRatio);
			y2 = yMid + (yDiff / 2.0) * (pageAspectRatio / worldAspectRatio);
		}
		
		/*
		 * Setup CTM from world coordinates to page coordinates.
		 */
		mWorldCtm = new AffineTransform();
		mWorldCtm.scale(mOutputFormat.getPageWidth() / (x2 - x1),
			mOutputFormat.getPageHeight() / (y2 - y1));
		mWorldCtm.translate(-x1, -y1);
		mWorldExtents = new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);
		mWorldUnits = units;
	}

	/**
	 * Sets reprojection between two world coordinate systems.
	 * @param sourceSystem description of coordinate system coordinates transformed form.
	 * @param destinationSystem description of coordinate system coordinates
	 * are transformed to.
	 */
	public void setReprojection(String sourceSystem, String destinationSystem)
		throws MapyrusException
	{
		mProjectionTransform = new WorldCoordinateTransform(sourceSystem,
			destinationSystem);
	}

	/**
	 * Set current dataset that can be queried and fetched from.
	 * @param dataset opened dataset for subsequent queries.
	 */
	public void setDataset(GeographicDataset dataset) throws MapyrusException
	{
		/*
		 * Clear any previous dataset defined in this context.
		 */
		if (mDataset != null && mDatasetDefined)
			mDataset.close();
		mDataset = new Dataset(dataset);
		mDatasetDefined = true;
	}

	/**
	 * Returns scaling factor in current transformation.
	 * @return scale value.
	 */
	public double getScaling()
	{
		return(mScaling);
	}

	/**
	 * Returns rotation angle in current transformation.
	 * @return rotation in radians.
	 */
	public double getRotation()
	{
		return(mRotation);
	}

	/**
	 * Returns world coordinate extents being shown on page.
	 * @return rectangular area covered by extents.
	 */
	public Rectangle2D.Double getWorldExtents() throws MapyrusException
	{
		Rectangle2D.Double retval;

		if (mWorldExtents != null)
		{
			retval = mWorldExtents;
		}
		else if (mOutputFormat != null)
		{
			retval = new Rectangle2D.Double(0, 0,
				mOutputFormat.getPageWidth(), mOutputFormat.getPageHeight());
				
		}
		else
		{
			retval = new Rectangle2D.Double();
		}
		return(retval);
	}

	/**
	 * Return scale of world coordinates.  The world coordinate range divided
	 * by the page size.
	 * @return scale, (1:2000) is returned as value 2000. 
	 */
	public double getWorldScale()
	{
		double scale;
		double worldWidthInMM;

		if (mOutputFormat != null && mWorldCtm != null)
		{
			worldWidthInMM = mWorldExtents.width;
			if (mWorldUnits == WORLD_UNITS_METRES)
				worldWidthInMM *= 1000.0;
			else if (mWorldUnits == WORLD_UNITS_FEET)
				worldWidthInMM *= (1000.0 / 0.3048);
			else
				worldWidthInMM *= (110000 * 1000.0);

			scale = worldWidthInMM / mOutputFormat.getPageWidth();
		}
		else
		{
			scale = 1.0;
		}
		return(scale);
	}

	/**
	 * Returns bounding that when transformed through projection results
	 * in same bounding box as current world coordinate system.
	 * @return bounding box.
	 */
	public Rectangle2D.Double getUnprojectedExtents() throws MapyrusException
	{
		Rectangle2D.Double retval;
		double xMin, yMin, xMax, yMax;
		int i, j;
		double coords[] = new double[2];
		
		xMin = yMin = Float.MAX_VALUE;
		xMax = yMax = Float.MIN_VALUE;

		if (mWorldExtents != null)
		{
			if (mProjectionTransform != null)
			{
				/*
				 * Transform points around boundary of world coordinate extents
				 * backwards through projection transformation.
				 * Find minimum and maximum values.
				 */
				for (i = 0; i <= PROJECTED_GRID_STEPS; i++)
				{
					for (j = 0; j <= PROJECTED_GRID_STEPS; j++)
					{
						/*
						 * Only transform points around boundary.
						 */
						if ((i == 0 || i == PROJECTED_GRID_STEPS) &&
							(j == 0 || j == PROJECTED_GRID_STEPS))
						{
							coords[0] = mWorldExtents.x +
								((double)i / PROJECTED_GRID_STEPS) * mWorldExtents.width;
							coords[1] = mWorldExtents.y +
								((double)j / PROJECTED_GRID_STEPS) * mWorldExtents.height;	
						
							mProjectionTransform.backwardTransform(coords);
							if (coords[0] < xMin)
								xMin = coords[0];
							if (coords[1] < yMin)
								yMin = coords[1];
							if (coords[0] > xMax)
								xMax = coords[0];
							if (coords[1] > yMax)
								yMax = coords[1];
						}
					}
				}
				retval = new Rectangle2D.Double(xMin, yMin, xMax - xMin, yMax - yMin);
			}
			else
			{
				/*
				 * No projection transformation set so just return plain world
				 * coordinate extents.
				 */
				retval = mWorldExtents;
			}
		}
		else if (mOutputFormat != null)
		{
			/*
			 * No world coordinate system set, just return page coordinate.
			 */
			retval = new Rectangle2D.Double(0, 0,
				mOutputFormat.getPageWidth(), mOutputFormat.getPageHeight());
		}
		else
		{
			retval = new Rectangle2D.Double(0, 0, 1, 1);
		}
		return(retval);
	}
						
	/**
	 * Get dataset currently being queried.
	 * @return dataset being queried, or null if not dataset is being queried.
	 */
	public Dataset getDataset()
	{
		return(mDataset);
	}

	/**
	 * Get name of procedure block containing statements currently being executed.
	 * @return procedure block name, or null if outside of any procedure block.
	 */
	public String getBlockName()
	{
		return(mBlockName);
	}

	/**
	 * Add point to path.
	 * @param x X coordinate to add to path.
	 * @param y Y coordinate to add to path.
	 */
	public void moveTo(double x, double y) throws MapyrusException
	{
		double srcPts[] = new double[2];
		float dstPts[] = new float[2];

		srcPts[0] = x;
		srcPts[1] = y;

		/*
		 * Transform to correct world coordinate system.
		 */
		if (mProjectionTransform != null)
		{
			mProjectionTransform.forwardTransform(srcPts);
		}
		
		/*
		 * Transform point from world coordinates
		 * to millimetre position on page.
		 */		
		if (mWorldCtm != null)
			mWorldCtm.transform(srcPts, 0, srcPts, 0, 1);
		mCtm.transform(srcPts, 0, dstPts, 0, 1);
		if (mPath == null)
			mPath = new GeometricPath();

		/*
		 * Set no rotation for point.
		 */
		mPath.moveTo(dstPts[0], dstPts[1], 0.0f);
	}

	/**
	 * Add point to path with straight line segment from last point.
	 * @param x X coordinate to add to path.
	 * @param y Y coordinate to add to path.
	 */
	public void lineTo(double x, double y) throws MapyrusException
	{
		double srcPts[] = new double[2];
		float dstPts[] = new float[2];

		srcPts[0] = x;
		srcPts[1] = y;

		/*
		 * Make sure that a start point for path was defined.
		 */
		if (mPath == null || mPath.getMoveToCount() == 0)
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_MOVETO));
		
		/*
		 * Transform to correct world coordinate system.
		 */
		if (mProjectionTransform != null)
		{
			mProjectionTransform.forwardTransform(srcPts);
		}

		/*
		 * Transform point from world coordinates
		 * to millimetre position on page.
		 */
		if (mWorldCtm != null)
			mWorldCtm.transform(srcPts, 0, srcPts, 0, 1);
		mCtm.transform(srcPts, 0, dstPts, 0, 1);
		if (mPath == null)
			mPath = new GeometricPath();
		mPath.lineTo(dstPts[0], dstPts[1]);
	}

	/**
	 * Add circular arc to path from last point to a new point, given centre and direction.
	 * @param direction positive for clockwise, negative for anti-clockwise. 
	 * @param xCentre X coordinate of centre point of arc.
	 * @param yCentre Y coordinate of centre point of arc.
	 * @param xEnd X coordinate of end point of arc.
	 * @param yEnd Y coordinate of end point of arc.
	 */
	public void arcTo(int direction, double xCentre, double yCentre,
		double xEnd, double yEnd) throws MapyrusException
	{
		double centrePts[] = new double[2];
		double endPts[] = new double[2];
		float dstPts[] = new float[4];

		centrePts[0] = xCentre;
		centrePts[1] = yCentre;
		endPts[0] = xEnd;
		endPts[1] = yEnd;

		/*
		 * Make sure that a start point for arc was defined.
		 */
		if (mPath == null || mPath.getMoveToCount() == 0)
				throw new MapyrusException(MapyrusMessages.get(MapyrusMessages.NO_ARC_START));
		
		/*
		 * Transform to correct world coordinate system.
		 */
		if (mProjectionTransform != null)
		{
			mProjectionTransform.forwardTransform(centrePts);
			mProjectionTransform.forwardTransform(endPts);
		}

		/*
		 * Transform points from world coordinates
		 * to millimetre position on page.
		 */
		if (mWorldCtm != null)
		{
			mWorldCtm.transform(centrePts, 0, centrePts, 0, 1);
			mWorldCtm.transform(endPts, 0, endPts, 0, 1);
		}
		mCtm.transform(centrePts, 0, dstPts, 0, 1);
		mCtm.transform(endPts, 0, dstPts, 2, 1);

		mPath.arcTo(direction, dstPts[0], dstPts[1], dstPts[2], dstPts[3]);
	}
	
	/**
	 * Clears currently defined path.
	 */
	public void clearPath()
	{
		/*
		 * If a path was defined then clear it.
		 * If no path then clear any path we are using from another
		 * context too.
		 */
		if (mPath != null)
			mPath.reset();
		else
			mExistingPath = null;
	}

	/**
	 * Replace path with regularly spaced points along it.
	 * @param spacing is distance between points.
	 * @param offset is starting offset of first point.
	 */
	public void samplePath(double spacing, double offset) throws MapyrusException
	{
		GeometricPath path = getDefinedPath();
		double resolution = getResolution();

		if (path != null)
		{
			mPath = path.samplePath(spacing * mScaling,
				offset * mScaling, resolution);
		}
	}

	/**
	 * Replace path defining polygon with parallel stripe
	 * lines covering the polygon.
	 * @param spacing is distance between stripes.
	 * @param angle is angle of stripes, in radians, with zero horizontal.
	 */
	public void stripePath(double spacing, double angle)
	{
		GeometricPath path = getDefinedPath();

		if (path != null)
			mPath = path.stripePath(spacing * mScaling, angle);
	}

	/**
	 * Shift all coordinates in path shifted by a fixed amount.
	 * @param xShift distance in millimetres to shift X coordinate values.
	 * @param yShift distance in millimetres to shift Y coordinate values.
	 */
	public void translatePath(double xShift, double yShift)
	{
		GeometricPath path = getDefinedPath();
		double coords[] = new double[2];
		coords[0] = xShift;
		coords[1] = yShift;

		/*
		 * Scale and rotate shift to current transformation matrix.
		 */
		if (!mCtm.isIdentity())
		{
			AffineTransform at = AffineTransform.getRotateInstance(mRotation);
			at.scale(mScaling, mScaling);
		
			at.transform(coords, 0, coords, 0, 1);
		}

		if (path != null)
			mPath = path.translatePath(coords[0], coords[1]);
	}

	/**
	 * Draw image icon at current point on path.
	 * @param icon icon to draw.
	 * @param size size for icon in millimetres.
	 */
	public void drawIcon(ImageIcon icon, double size)
		throws IOException, MapyrusException
	{
		GeometricPath path = getDefinedPath();

		if (path != null && mOutputFormat != null)
		{
			setGraphicsAttributes(ATTRIBUTE_CLIP);
			mOutputFormat.drawIcon(path.getMoveTos(), icon, size, mRotation, mScaling);
		}
	}

	/**
	 * Draw currently defined path.
	 */
	public void stroke() throws IOException, MapyrusException
	{
		GeometricPath path = getDefinedPath();

		if (path != null && mOutputFormat != null)
		{
			setGraphicsAttributes(ATTRIBUTE_COLOR|ATTRIBUTE_LINESTYLE|ATTRIBUTE_CLIP);
			mOutputFormat.stroke(path.getShape());
		}
	}

	/**
	 * Fill currently defined path.
	 */
	public void fill() throws IOException, MapyrusException
	{
		GeometricPath path = getDefinedPath();
		
		if (path != null && mOutputFormat != null)
		{	
			setGraphicsAttributes(ATTRIBUTE_COLOR|ATTRIBUTE_CLIP);
			mOutputFormat.fill(path.getShape());
		}
	}

	/**
	 * Clip to show only area outside currently defined path,
	 * protecting what is inside path.
	 */
	public void protect() throws MapyrusException
	{
		GeometricPath path = getDefinedPath();
		GeometricPath protectedPath;

		if (path != null && mOutputFormat != null)
		{
			/*
			 * Add a rectangle around the edge of the page as the new polygon
			 * perimeter.  The path becomes an island in the polygon (with
			 * opposite direction so winding rule works) and only
			 * the area outside the path is then visible.  
			 */
			float width = (float)(mOutputFormat.getPageWidth());
			float height = (float)(mOutputFormat.getPageHeight());

			protectedPath = new GeometricPath();
			protectedPath.moveTo(0.0f, 0.0f, 0.0);
			if (path.isClockwise(getResolution()))
			{
				/*
				 * Outer rectange should be anti-clockwise.
				 */
				protectedPath.lineTo(width, 0.0f);
				protectedPath.lineTo(width, height);
 				protectedPath.lineTo(0.0f, height);
			}
			else
			{
				/*
				 * Outer rectangle should be clockwise.
				 */
				protectedPath.lineTo(0.0f, height);
				protectedPath.lineTo(width, height);
				protectedPath.lineTo(width, 0.0f);
			}
			protectedPath.closePath();
			protectedPath.append(path, false);

			mAttributesPending |= ATTRIBUTE_CLIP;
			mAttributesChanged |= ATTRIBUTE_CLIP;
			mOutputFormat.clip(protectedPath.getShape());

			/*
			 * Add this polygon to list of paths we are clipping against.
			 */
			if (mClippingPaths == null)
				mClippingPaths = new ArrayList();
			mClippingPaths.add(protectedPath);
		}
	}

	/**
	 * Clip to show only inside of currently defined path.
	 */
	public void clip()
	{
		GeometricPath path = getDefinedPath();
		GeometricPath clipPath;

		if (path != null && mOutputFormat != null)
		{
			clipPath = new GeometricPath(path);
			if (mClippingPaths == null)
				mClippingPaths = new ArrayList();
			mClippingPaths.add(clipPath);
			mAttributesPending |= ATTRIBUTE_CLIP;
			mAttributesChanged |= ATTRIBUTE_CLIP;
			if (mOutputFormat != null)
			{
				mOutputFormat.clip(clipPath.getShape());
			}
		}
	}

	/**
	 * Draw label positioned at (or along) currently defined path.
	 * @param label is string to draw on page.
	 */
	public void label(String label) throws IOException, MapyrusException
	{
		GeometricPath path = getDefinedPath();
		
		if (path != null && mOutputFormat != null)
		{	
			setGraphicsAttributes(ATTRIBUTE_COLOR|ATTRIBUTE_FONT|ATTRIBUTE_JUSTIFY|ATTRIBUTE_CLIP);
			mOutputFormat.label(path.getMoveTos(), label);
		}
	}

	/**
	 * Returns the number of moveTo's in path defined in this context.
	 * @return count of moveTo calls made.
	 */
	public int getMoveToCount()
	{
		int retval;
		GeometricPath path = getDefinedPath();

		if (path == null)
			retval = 0;
		else
			retval = path.getMoveToCount();
		return(retval);
	}

	/**
	 * Returns the number of lineTo's in path defined in this context.
	 * @return count of lineTo calls made for this path.
	 */
	public int getLineToCount()
	{
		int retval;
		GeometricPath path = getDefinedPath();

		if (path == null)
			retval = 0;
		else
			retval = path.getLineToCount();
		return(retval);
	}

	/**
	 * Returns geometric length of current path.
	 * @return length of current path.
	 */
	public double getPathLength() throws MapyrusException
	{
		double retval;
		GeometricPath path = getDefinedPath();
		double resolution = getResolution();
		
		if (path == null)
			retval = 0.0;
		else
			retval = path.getLength(resolution);
		return(retval);
	}

	/**
	 * Returns geometric area of current path.
	 * @return area of current path.
	 */
	public double getPathArea() throws MapyrusException
	{
		double retval;
		GeometricPath path = getDefinedPath();
		double resolution = getResolution();
		
		if (path == null)
			retval = 0.0;
		else
			retval = path.getArea(resolution);
		return(retval);
	}

	/**
	 * Returns geometric centroid of current path.
	 * @return centroid of current path.
	 */
	public Point2D.Double getPathCentroid() throws MapyrusException
	{
		Point2D.Double retval;
		GeometricPath path = getDefinedPath();
		double resolution = getResolution();
		
		if (path == null)
			retval = new Point2D.Double();
		else
			retval = path.getCentroid(resolution);
		return(retval);
	}

	/**
	 * Returns rotation angle for each each moveTo point in current path
	 * @return array of rotation angles relative to rotation in current 
	 * transformation matrix. 
	 */
	public ArrayList getMoveToRotations()
	{
		ArrayList retval;

		GeometricPath path = getDefinedPath();

		if (path == null)
			retval = null;
		else
			retval = path.getMoveToRotations();
		return(retval);
	}

	/**
	 * Returns coordinate for each each moveTo point in current path.
	 * @return array of Point2D.Float objects relative to current transformation matrix.
	 */
	public ArrayList getMoveTos() throws MapyrusException
	{
		ArrayList retval = null;
		GeometricPath path = getDefinedPath();
		AffineTransform inverse;
		ArrayList moveTos;

		try
		{
			if (path != null)
			{
				/*
				 * If there is no transformation matrix then we can return original
				 * coordinates, otherwise we must convert all coordinates to be relative
				 * to the current transformation matrix and build a new list.
				 */
				if (mCtm.isIdentity())
				{
					retval = path.getMoveTos();
				}
				else
				{
					inverse = mCtm.createInverse();
					moveTos = path.getMoveTos();
					retval = new ArrayList(moveTos.size());

					for (int i = 0; i < moveTos.size(); i++)
					{
						Point2D.Float pt = (Point2D.Float)(moveTos.get(i));
						retval.add(inverse.transform(pt, null));
					}
				}
			}
		}
		catch (NoninvertibleTransformException e)
		{
			throw new MapyrusException(e.getMessage());
		}
		return(retval);
	}

	/**
	 * Returns bounding box of this geometry.
	 * @return bounding box, or null if no path is defined.
	 */
	public Rectangle2D getBounds2D()
	{
		Rectangle2D bounds;
		GeometricPath path = getDefinedPath();

		if (path == null)
			bounds = null;
		else
			bounds = path.getBounds2D();

		return(bounds);
	}

	/**
	 * Returns value of a variable.
	 * @param variable name to lookup.
	 * @return value of variable, or null if it is not defined.
	 */
	public Argument getVariableValue(String varName)
	{
		Argument retval;
		
		/*
		 * Variable is not set if no lookup table is defined.
		 */
		if (mVars == null)
			retval = null;
		else		
			retval = (Argument)mVars.get(varName);
			
		return(retval);
	}

	/**
	 * Indicates that a variable is to be stored locally in this context
	 * and not be made available to other contexts.
	 * @param varName name of variable to be treated as local.
	 */
	public void setLocalScope(String varName)
	{
		/*
		 * Record that variable is local.
		 */
		if (mLocalVars == null)
			mLocalVars = new HashSet();
		mLocalVars.add(varName);
	}

	/**
	 * Returns true if variable has been defined local in this context
	 * with @see setLocalScope().
	 * @param varName is name of variable to check.
	 * @return true if variable defined local.
	 */
	public boolean hasLocalScope(String varName)
	{
		return(mLocalVars != null && mLocalVars.contains(varName));
	}

	/**
	 * Define variable in current context, replacing any existing
	 * variable with the same name.
	 * @param varName name of variable to define.
	 * @param value is value for this variable
	 */
	public void defineVariable(String varName, Argument value)
	{
		/*
		 * Create new variable.
		 */
		if (mVars == null)
			mVars = new HashMap();

		/*
		 * Clone hashmap variables to avoid changes to entries
		 * in one variable being visible to others.
		 */
		if (value.getType() == Argument.HASHMAP)
			value = (Argument)value.clone();
		mVars.put(varName, value);
	}

	/**
	 * Define an key-value entry in a hashmap in current context,
	 * replacing any existing entry with the same key.
	 * @param hashMapName name of hashmap to add entry to.
	 * @param key is key to add.
	 * @param value is value to add.
	 */
	public void defineHashMapEntry(String hashMapName, String key, Argument value)
	{
		if (mVars == null)
			mVars = new HashMap();

		/*
		 * Create new entry in a hash map.
		 */
		Argument arg = (Argument)mVars.get(hashMapName);
		if (arg == null || arg.getType() != Argument.HASHMAP)
		{
			/*
			 * No hash map with this name used before,
			 * create new one.
			 */
			arg = new Argument();
			mVars.put(hashMapName, arg);
			
		}
		arg.addHashMapEntry(key, value);
	}
}
