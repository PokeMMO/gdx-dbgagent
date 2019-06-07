package gdxdbg;

import java.lang.instrument.Instrumentation;

/**
 * @author Desu
 */
public class DgbAgent
{
	public static void premain(String args, Instrumentation instrumentation)
	{
		instrumentation.addTransformer(new DgbAgentClassFileTransformer(), true);
	}
}
