/**
 * Contexts for interpretation that are pushed and popped as procedure 
 * blocks are called and return so that changes in a procedure block
 * are local to that block.
 */

/*
 * $Id$
 */
import java.util.Hashtable;
import java.awt.Color;
import java.io.IOException;
import java.awt.Shape;
import java.util.LinkedList;
import java.util.Date;

public class ContextStack
{
	/*
	 * Maximum allowed stacking of contexts.
	 * Any deeper is probably infinite recursion.
	 */
	private static final int MAX_STACK_LENGTH = 30;
	
	/*
	 * Stack of contexts, with current context in last slot.
	 */
	private LinkedList mStack;
	
	/**
	 * Create new stack of contexts to manage state as procedure blocks
	 * are called.
	 */
	public ContextStack()
	{
		mStack = new LinkedList();
		mStack.add(new Context());
	}

	/**
	 * Get current context from top of stack.
	 * @return current context.
	 */
	private Context getCurrentContext()
	{
		return((Context)mStack.getLast());
	}

	/**
	 * Pops current context from stack.
	 * @return number of elements left in stack after pop.
	 */
	private int popContext() throws IOException, MapyrusException
	{
		int i = mStack.size();
		
		if (i > 0)
		{
			/*
			 * Finish off current context, remove it from stack.
			 */
			boolean attributesSet = getCurrentContext().closeContext();
			mStack.removeLast();
			
			/*
			 * If graphics attributes were set in context then set them changed
			 * in the context that is now current.
			 */
			if (attributesSet)
				getCurrentContext().setAttributesChanged();

			i--;
		}
		return(i);
	}

	/**
	 * Pushes copy of context at top of stack onto stack.
	 * This context is later removed with popContext().
	 */
	private void pushContext() throws MapyrusException
	{
		if (mStack.size() == MAX_STACK_LENGTH)
		{
			throw new MapyrusException("Procedure block nesting too deep");
		}
		mStack.add(new Context(getCurrentContext()));
	}
				
	/**
	 * Sets output file for drawing to.
	 * @param filename name of image file output will be saved to
	 * @param width is the page width (in points).
	 * @param height is the page height (in points).
	 * @param extras contains extra settings for this output.
	 */
	public void setOutputFormat(String filename,
		int width, int height, String extras)
		throws IOException, MapyrusException
	{
		getCurrentContext().setOutputFormat(filename, width, height, extras);
	}

	/**
	 * Sets line width.
	 * @param width is width for lines in millimetres.
	 */
	public void setLineWidth(double width)
	{
		getCurrentContext().setLineWidth(width);
	}

	/**
	 * Sets color.
	 * @param c is new color for drawing.
	 */
	public void setColor(Color c)
	{
		getCurrentContext().setColor(c);
	}
	
	/**
	 * Sets scaling for subsequent coordinates.
	 * @param x is new scaling in X axis.
	 * @param y is new scaling in Y axis.
	 */
	public void setScaling(double x, double y)
	{
		getCurrentContext().setScaling(x, y);
	}

	/**
	 * Add point to path.
	 * @param x X coordinate to add to path.
	 * @param y Y coordinate to add to path.
	 */
	public void moveTo(double x, double y)
	{
		getCurrentContext().moveTo(x, y);
	}

	/**
	 * Add point to path with straight line segment from last point.
	 * @param x X coordinate to add to path.
	 * @param y Y coordinate to add to path.
	 */
	public void lineTo(double x, double y)
	{
		getCurrentContext().lineTo(x, y);
	}

	/**
	 * Resets path to empty.
	 */
	public void clearPath()
	{
		getCurrentContext().clearPath();
	}
	
	/**
	 * Draw currently defined path.
	 */
	public void stroke()
	{
		getCurrentContext().stroke();
	}

	/**
	 * Fill currently defined path.
	 */
	public void fill()
	{
		getCurrentContext().fill();
	}	
		
	/**
	 * Returns value of a variable.
	 * @param variable name to lookup.
	 * @return value of variable, or null if it is not defined.
	 */
	public Argument getVariableValue(String varName)
	{
		Argument retval = null;
		
		if (varName.startsWith(Mapyrus.PROGRAM_NAME + "."))
		{
			String sub = varName.substring(Mapyrus.PROGRAM_NAME.length() + 1);
		
			/*
			 * Return internal/system variable.
			 */
			if (sub.equals("random"))
			{
				retval = new Argument(Math.random());
			}
			else if (sub.equals("timestamp"))
			{
				Date now = new Date();
				retval = new Argument(Argument.STRING, now.toString());
			}
			else
			{
				/*
				 * Get value from system properties.
				 */
				try
				{
					String property = System.getProperty(sub);
					if (property != null)
						retval = new Argument(Argument.STRING, property);
					else
						retval = new Argument(Argument.STRING, "undef");
				}
				catch (SecurityException e)
				{
					retval = new Argument(Argument.STRING, "undef");
				}
			}
		}
		else
		{
			/*
			 * Search back through stack for a context defining
			 * this variable.
			 */
			int i = mStack.size() - 1;
			Context context;
			
			while (i >= 0 && retval == null)
			{
				context = (Context)mStack.get(i);
				retval = context.getVariableValue(varName);
			}
		}
		return(retval);
	}
	
	/**
	 * Define a variable in current context, replacing any existing
	 * variable of the same name.
	 * @param varName name of variable to define.
	 * @param value is value for this variable
	 */
	public void defineVariable(String varName, Argument value)
	{
		getCurrentContext().defineVariable(varName, value);
	}
	
	/**
	 * Save current context so that it can be restored later with restoreState.
	 */
	public void saveState() throws MapyrusException
	{
		pushContext();
	}
	
	/**
	 * Restore context to state before saveState was called.
	 */
	public void restoreState() throws IOException, MapyrusException
	{
		popContext();
	}
	
	/**
	 * Pops all contexts from stack that were pushed with saveState.
	 * A ContextStack cannot be used again after this call.
	 */
	public void closeContextStack() throws IOException, MapyrusException
	{
		while (popContext() > 0)
			;
	}
}
