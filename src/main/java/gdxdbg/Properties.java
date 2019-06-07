package gdxdbg;

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
	 * Enables debugging if a dispose method is called multiple times.<br/>
	 * Not recommended to use, double dispose calls should be made safe if not already.
	 */
	public static final boolean DEBUG_DOUBLE_DISPOSE = parseProperty("gdxdbg.debug.double_dispose", false);
	/**
	 * Enables debugging if certain `modifiable constant`'s values change during runtime.<br/>
	 * Certain constants like `Color.WHITE` can be accidently modified, leading to unexpected results.<br/>
	 * This utility will alert you that this has occurred, but not what has caused it.
	 */
	public static final boolean DEBUG_MODIFIABLE_CONSTANTS = parseProperty("gdxdbg.debug.modifiable_constants", true);
	
	private static boolean parseProperty(String name, boolean def)
	{
		String value = System.getProperty(name);
		if (value != null)
			return value.equals("") || Boolean.valueOf(value);
		return def;
	}
}
