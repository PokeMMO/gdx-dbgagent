package gdxdbg;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

/**
 * {@link ClassFileTransformer} which performs class transformations to add various debugging utilities.
 * @author Desu
 */
public class DgbAgentClassFileTransformer implements ClassFileTransformer
{
	public static final String FIELD_NAME_EXCEPTION = "$$$disposeexception";
	public static final String FIELD_NAME_EXCEPTION_DOUBLE_DISPOSE = "$$$doubledisposeexception";
	
	private ModifiableConstants modifiableConstants = new ModifiableConstants();
	private GLThreadVerification glThreadVerification = new GLThreadVerification();
	
	private static CtClass[] EMPTY_CT_CLASSES = new CtClass[0];

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
	{
		if(className.startsWith("java/") || className.startsWith("javax/"))
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
		{
			modified = true;
		}
		
		boolean isDisposable = false;
		try
		{
			CtClass disposable = cp.get("com.badlogic.gdx.utils.Disposable");
			isDisposable = isDisposable(clazz, disposable);
		}
		catch(NotFoundException e)
		{
			// Don't care
		}

		boolean foundValidDisposeMethod = false;
		if(isDisposable)
		{
			try
			{
				CtMethod method = clazz.getDeclaredMethod("dispose", EMPTY_CT_CLASSES);
				foundValidDisposeMethod = isDisposible(method);
			}
			catch(NotFoundException e)
			{
				// Don't care
			}
		}
		
		if(foundValidDisposeMethod)
		{
			CtClass eo = cp.get(ModifiedDisposible.class.getName());
			for(CtClass i : clazz.getInterfaces())
			{
				if(i.equals(eo))
				{
					throw new RuntimeException("Class already implements ModifiedDisposible interface");
				}
			}
			
			writeDisposibleObjectImpl(clazz);
			modified = true;
		}

		if(modified)
		{
			if(Properties.TRACE)
				System.out.println(clazz.getName() + " was modified.");
			return clazz.toBytecode();
		}

		return null;
	}

	private boolean isDisposable(CtClass clazz, CtClass disposable) throws Exception
	{
		if(clazz == null)
			return false;
		
		if(clazz.getName().startsWith("com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier") || clazz.getName().startsWith("com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent"))
			return false;
		
		// These aren't necessarily required to be disposed, and their sub resources will warn us of issues
		if(clazz.getName().startsWith("com.badlogic.gdx.graphics.g2d.PixmapPacker") || clazz.getName().startsWith("com.badlogic.gdx.maps.Map"))
			return false;
		
		if(clazz.equals(disposable))
			return true;
		
		for(CtClass i : clazz.getInterfaces())
		{
			if(isDisposable(i, disposable))
				return true;
		}
		
		if(isDisposable(clazz.getSuperclass(), disposable))
			return true;

		return false;
	}

	protected void writeDisposibleObjectImpl(CtClass clazz) throws NotFoundException, CannotCompileException
	{
		ClassPool cp = clazz.getClassPool();
		clazz.addInterface(cp.get(ModifiedDisposible.class.getName()));
		writeDisposibleObjectFields(clazz);
		writeDisposibleObjectMethods(clazz);
	}

	private void writeDisposibleObjectFields(CtClass clazz) throws CannotCompileException, NotFoundException
	{
		ClassPool cp = clazz.getClassPool();

		if(Properties.DEBUG_UNDISPOSED)
		{
			// add object that holds path from initialization
			CtField cbField = new CtField(cp.get(RuntimeException.class.getName()), FIELD_NAME_EXCEPTION, clazz);
			cbField.setModifiers(Modifier.PRIVATE | Modifier.TRANSIENT);
			clazz.addField(cbField, CtField.Initializer.byExpr("new RuntimeException(\"Undisposed \"+getClass().getName()+\" resource\");"));
		}
		
		if(Properties.DEBUG_DOUBLE_DISPOSE)
		{
			// add object that holds path from dispose for double dispose calls
			CtField cbField = new CtField(cp.get(RuntimeException.class.getName()), FIELD_NAME_EXCEPTION_DOUBLE_DISPOSE, clazz);
			cbField.setModifiers(Modifier.PRIVATE | Modifier.TRANSIENT);
			clazz.addField(cbField);
		}
	}

	private void writeDisposibleObjectMethods(CtClass clazz) throws NotFoundException, CannotCompileException
	{
		if(Properties.DEBUG_UNDISPOSED)
		{
			CtMethod meth = null;
			try
			{
				meth = clazz.getDeclaredMethod("finalize", EMPTY_CT_CLASSES);
			}
			catch(NotFoundException e)
			{
				//Ignored
			}
			
			StringBuilder sb = new StringBuilder();
			if(clazz.getName().equals("com.badlogic.gdx.graphics.GLTexture"))
			{
				sb.append("if((this instanceof com.badlogic.gdx.graphics.Texture) || (this instanceof com.badlogic.gdx.graphics.Cubemap)){} else ");
			}
			sb.append("if("+FIELD_NAME_EXCEPTION+" != null) "+FIELD_NAME_EXCEPTION+".printStackTrace();");
			
			if(meth == null)
			{
				if(Properties.TRACE)
					System.out.println("finalize not found creating it");
				CtMethod m = CtNewMethod.make("public void finalize() throws Throwable { "+sb.toString()+"; super.finalize(); } ", clazz);
				clazz.addMethod(m);
			}
			else
			{
				if(Properties.TRACE)
					System.out.println("modifing " + clazz.getName() + "'s finalize method");
				meth.insertBefore("{ "+sb.toString()+" }");
			}
		}
		
		if(Properties.DEBUG_UNDISPOSED || Properties.DEBUG_DOUBLE_DISPOSE)
		{
			CtMethod meth = null;
			try
			{
				meth = clazz.getDeclaredMethod("dispose", EMPTY_CT_CLASSES);
			}
			catch(NotFoundException e)
			{
				//Ignored
			}

			if(meth != null)
			{
				if(Properties.DEBUG_UNDISPOSED)
					meth.insertBefore("{ "+FIELD_NAME_EXCEPTION+" = null; }");

				// Some are safe to double dispose
				if(Properties.DEBUG_DOUBLE_DISPOSE && !"com.badlogic.gdx.graphics.Texture".equals(clazz.getName()))
					meth.insertBefore("{ synchronized(this) { if("+FIELD_NAME_EXCEPTION_DOUBLE_DISPOSE+" != null) new RuntimeException(\"Double Dispose\", "+FIELD_NAME_EXCEPTION_DOUBLE_DISPOSE+").printStackTrace(); "
							+ FIELD_NAME_EXCEPTION_DOUBLE_DISPOSE+" = new RuntimeException(\"Previous Dispose\");"
							+ "} }");
			}
		}
	}

	protected boolean isDisposible(CtMethod method)
	{
		int modifiers = method.getModifiers();
		if(Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers) || Modifier.isStatic(modifiers))
			return false;
		
		return method.getName().equals("dispose");
	}
	
	public static interface ModifiedDisposible
	{
		
	}
}