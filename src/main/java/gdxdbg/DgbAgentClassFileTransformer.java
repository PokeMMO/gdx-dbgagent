package gdxdbg;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import gdxdbg.DisposableTransformer.ModifiedCloseable;
import gdxdbg.DisposableTransformer.ModifiedDisposible;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;

/**
 * {@link ClassFileTransformer} which performs class transformations to add various debugging utilities.
 * @author Desu
 */
public class DgbAgentClassFileTransformer implements ClassFileTransformer
{
	private DisposableTransformer disposableTransformer = new DisposableTransformer("dispose", "$$$disposeexception", "com.badlogic.gdx.utils.Disposable", ModifiedDisposible.class);
	private DisposableTransformer closeableTransformer = new DisposableTransformer("close", "$$$closeeexception", "java.io.Closeable", ModifiedCloseable.class);
	private ModifiableConstants modifiableConstants = new ModifiableConstants();
	private GLThreadVerification glThreadVerification = new GLThreadVerification();

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
	{
		if(!Properties.DEBUG_UNCLOSED && (className.startsWith("java/") || className.startsWith("javax/")))
			return null;
		
		try
		{
			return transformClass(loader, classfileBuffer);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	protected byte[] transformClass(ClassLoader loader, byte[] clazzBytes) throws Exception
	{
		ClassPool cp = new ClassPool();
		cp.appendClassPath(new LoaderClassPath(loader));
		CtClass clazz = cp.makeClass(new ByteArrayInputStream(clazzBytes));
		
		boolean modified = false;
		
		// Collect any modifiable constants to watch for unexpected changes
		if(modifiableConstants.containsModifiableConstants(clazz))
		{
			modifiableConstants.collectModifiableConstants(clazz);
			modified = true;
		}
		
		if(glThreadVerification.transform(clazz))
			modified = true;
		
		if(Properties.DEBUG_UNDISPOSED && disposableTransformer.transform(cp, clazz))
			modified = true;
		
		if(Properties.DEBUG_UNCLOSED && closeableTransformer.transform(cp, clazz))
			modified = true;

		if(modified)
		{
			if(Properties.TRACE)
				System.out.println(clazz.getName() + " was modified.");
			return clazz.toBytecode();
		}

		return null;
	}
}