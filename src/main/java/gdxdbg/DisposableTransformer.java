package gdxdbg;

import java.lang.reflect.Modifier;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

public class DisposableTransformer
{
	private static CtClass[] EMPTY_CT_CLASSES = new CtClass[0];
	
	public final String methodName;
	public final String exceptionFieldName;
	public final String interfaceClassName;
	public final Class<?> modifiedMarkerInterface;
	
	public DisposableTransformer(String methodName, String exceptionFieldName, String interfaceClassName, Class<?> modifiedMarkerInterface)
	{
		this.methodName = methodName;
		this.exceptionFieldName = exceptionFieldName;
		this.interfaceClassName = interfaceClassName;
		this.modifiedMarkerInterface = modifiedMarkerInterface;
	}
	
	public boolean transform(ClassPool cp, CtClass clazz) throws Exception
	{
		boolean hasInterface = false;
		try
		{
			CtClass interfaceClass = cp.get(interfaceClassName);
			hasInterface = hasInterface(clazz, interfaceClass);
		}
		catch(NotFoundException e)
		{
			// Don't care
		}

		boolean foundValidMethod = false;
		if(hasInterface)
		{
			try
			{
				CtMethod method = clazz.getDeclaredMethod(methodName, EMPTY_CT_CLASSES);
				foundValidMethod = isNormalMethod(method);
			}
			catch(NotFoundException e)
			{
				// Don't care
			}
		}
		
		if(foundValidMethod)
		{
			CtClass eo = cp.get(modifiedMarkerInterface.getName());
			for(CtClass i : clazz.getInterfaces())
			{
				if(i.equals(eo))
				{
					throw new RuntimeException("Class already implements " + modifiedMarkerInterface.getName() + " interface");
				}
			}
			
			writeObjectImpl(clazz);
			return true;
		}
		
		return false;
	}

	private boolean hasInterface(CtClass clazz, CtClass interfaceClass) throws Exception
	{
		if(clazz == null)
			return false;
		
		if(clazz.getName().startsWith("com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier") || clazz.getName().startsWith("com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent"))
			return false;
		
		// These aren't necessarily required to be disposed, and their sub resources will warn us of issues
		if(clazz.getName().startsWith("com.badlogic.gdx.graphics.g2d.PixmapPacker") || clazz.getName().startsWith("com.badlogic.gdx.maps.Map"))
			return false;
		
		if(clazz.equals(interfaceClass))
			return true;
		
		for(CtClass i : clazz.getInterfaces())
		{
			if(hasInterface(i, interfaceClass))
				return true;
		}
		
		if(hasInterface(clazz.getSuperclass(), interfaceClass))
			return true;

		return false;
	}

	protected void writeObjectImpl(CtClass clazz) throws NotFoundException, CannotCompileException
	{
		ClassPool cp = clazz.getClassPool();
		clazz.addInterface(cp.get(modifiedMarkerInterface.getName()));
		writeObjectFields(clazz);
		writeObjectMethods(clazz);
	}

	private void writeObjectFields(CtClass clazz) throws CannotCompileException, NotFoundException
	{
		ClassPool cp = clazz.getClassPool();

		// add object that holds path from initialization
		CtField cbField = new CtField(cp.get(RuntimeException.class.getName()), exceptionFieldName, clazz);
		cbField.setModifiers(Modifier.PRIVATE | Modifier.TRANSIENT);
		clazz.addField(cbField, CtField.Initializer.byExpr("new RuntimeException(\"Undisposed \"+getClass().getName()+\" resource\");"));
	}

	private void writeObjectMethods(CtClass clazz) throws NotFoundException, CannotCompileException
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
		sb.append("if("+exceptionFieldName+" != null) "+exceptionFieldName+".printStackTrace();");
		
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

		meth = null;
		try
		{
			meth = clazz.getDeclaredMethod(methodName, EMPTY_CT_CLASSES);
		}
		catch(NotFoundException e)
		{
			//Ignored
		}

		if(meth != null)
		{
			meth.insertBefore("{ "+exceptionFieldName+" = null; }");
		}
	}

	protected boolean isNormalMethod(CtMethod method)
	{
		int modifiers = method.getModifiers();
		if(Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers) || Modifier.isStatic(modifiers))
			return false;
		
		return true;
	}
	
	public static interface ModifiedDisposible
	{
		
	}
	
	public static interface ModifiedCloseable
	{
		
	}
}
