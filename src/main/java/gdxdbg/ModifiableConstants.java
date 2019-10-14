package gdxdbg;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;

/**
 * This class checks if certain values have been modified every 5 seconds and warns if they have.<br/>
 * Current implementation assumes {@link Object#toString()} shows all modifiable values.
 * @author Desu
 */
public class ModifiableConstants implements Runnable
{
	private Map<CtField, CtField> modifiableConstants = new HashMap<>();
	
	public ModifiableConstants()
	{
		if(Properties.DEBUG_MODIFIABLE_CONSTANTS)
		{
			Thread t = new Thread(this, "gdx-dbgagent-watchdog");
			t.setDaemon(true);
			t.start();
		}
	}
	
	@Override
	public void run()
	{
		while(true)
		{
			if(Properties.TRACE)
				System.out.println("Verifying for modifiable constants...");
			
			synchronized(modifiableConstants)
			{
				for(Entry<CtField, CtField> entry : modifiableConstants.entrySet())
				{
					try
					{
						CtField f = entry.getKey();
						CtField clone = entry.getValue();
						
						Class<?> clazz = Class.forName(f.getDeclaringClass().getName());
						
						Field field1 = clazz.getDeclaredField(f.getName());
						field1.setAccessible(true);
						Field field2 = clazz.getDeclaredField(clone.getName());
						field2.setAccessible(true);
						
						Object value1 = field1.get(null);
						Object value2 = field2.get(null);
						
						if(value1 == null || value2 == null)
							continue;
						
						if(!value1.toString().equals(value2))
						{
							System.err.println(f + " modifiable constant value has changed from " + value2 + " to " + value1.toString() +"!");
						}
					}
					catch(ReflectiveOperationException | SecurityException e)
					{
						
					}
				}
			}
			
			try
			{
				Thread.sleep(5000);
			}
			catch (InterruptedException e)
			{
			}
		}
	}
	
	protected boolean containsModifiableConstants(CtClass clazz)
	{
		if(!Properties.DEBUG_MODIFIABLE_CONSTANTS)
			return false;
		
		if("com.badlogic.gdx.math.Vector3".equals(clazz.getName()) || "com.badlogic.gdx.graphics.Color".equals(clazz.getName()))
			return true;
		
		return false;
	}
	
	protected void collectModifiableConstants(CtClass clazz) throws CannotCompileException
	{
		CtField[] fields = clazz.getDeclaredFields();
		for(CtField field : fields)
		{
			try
			{
				//Primitives aren't relevant here
				if(field.getType().isPrimitive())
					continue;
				
				//We only care about static fields
				if((field.getModifiers() & Modifier.STATIC) == 0)
					continue;
				

				if(clazz.getName().equals("com.badlogic.gdx.math.Vector3"))
				{
					switch(field.getName())
					{
						case "X":
						case "Y":
						case "Z":
						case "Zero":
							add(clazz, field);
							break;
					}
				}
				else
				{
					add(clazz, field);
				}
			}
			catch(NotFoundException e)
			{
				//Should never happen
				e.printStackTrace();
			}
		}
	}

	private void add(CtClass clazz, CtField field) throws CannotCompileException
	{
		if(Properties.TRACE)
			System.out.println("Adding modifiable constant watch on " + field);

		// Create a new field to store the string value
		CtField clone = CtField.make("public static transient String " + "$$$"+field.getName()+";", clazz);
		clazz.addField(clone);
		
		// Immediately store the toString value from the static initializer
		CtConstructor init = clazz.makeClassInitializer();
		init.insertAfter(clone.getName() + " = " + field.getName()+".toString();");
		
		synchronized(modifiableConstants)
		{
			modifiableConstants.put(field, clone);
		}
	}
}
