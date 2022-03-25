package gdxdbg;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Basic configurable options
 * @author Desu
 */
public class Properties
{
	/**
	 * Enables various agent debug statements.
	 */
	public static final boolean TRACE = parseProperty("gdxdbg.trace", false);
	/**
	 * Enables debugging if a Disposable object is finalized without being properly disposed.
	 */
	public static final boolean DEBUG_UNDISPOSED = parseProperty("gdxdbg.debug.undisposed", true);
	/**
	 * Enables debugging if a java.io.Closeable object is finalized without being properly disposed.
	 */
	public static final boolean DEBUG_UNCLOSED = parseProperty("gdxdbg.debug.unclosed", true);
	/**
	 * Enables debugging if certain `modifiable constant`'s values change during runtime.<br/>
	 * Certain constants like `Color.WHITE` can be accidently modified, leading to unexpected results.<br/>
	 * This utility will alert you that this has occurred, but not what has caused it.
	 */
	public static final boolean DEBUG_MODIFIABLE_CONSTANTS = parseProperty("gdxdbg.debug.modifiable_constants", true);
	/**
	 * Enables debugging of methods/constructors called from incorrect threads.
	 */
	public static final boolean DEBUG_GL_THREAD = parseProperty("gdxdbg.debug.gl_thread", true);
	
	private static boolean parseProperty(String name, boolean def)
	{
		String value = System.getProperty(name);
		if (value != null)
			return value.equals("") || Boolean.valueOf(value);
		return def;
	}
	
	static
	{
		try
		{
			for (Field f : Properties.class.getDeclaredFields())
			{
				if (!Modifier.isStatic(f.getModifiers()))
					continue;
				
				String property = "";
				switch(f.getName())
				{
					case "TRACE":
						property = "gdxdbg.trace";
						break;
					case "DEBUG_UNDISPOSED":
						property = "gdxdbg.debug.undisposed";
						break;
					case "DEBUG_UNCLOSED":
						property = "gdxdbg.debug.unclosed";
						break;
					case "DEBUG_MODIFIABLE_CONSTANTS":
						property = "gdxdbg.debug.modifiable_constants";
						break;
					case "DEBUG_GL_THREAD":
						property = "gdxdbg.debug.gl_thread";
						break;
				}
				
				System.out.println("[gdx-dbgagent] " + f.getName() + " -D" + property + "=" + f.get(null));
			}
		}
		catch(Exception e)
		{
			
		}
	}
}
