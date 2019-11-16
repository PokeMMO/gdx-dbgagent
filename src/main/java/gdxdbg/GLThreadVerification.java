package gdxdbg;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * This class adds verifications that certain method calls are made from the main OpenGL thread.
 * @author Desu
 */
public class GLThreadVerification
{
	public static Thread GL_THREAD = null;

	public boolean transform(CtClass clazz) throws Exception
	{
		if(!Properties.DEBUG_GL_THREAD)
			return false;
		
		try
		{
			if(isGraphicsImpl(clazz))
			{
				transformGraphicsImpl(clazz);
				return true;
			}
			
			if(addGLThreadChecks(clazz))
			{
				return true;
			}
		}
		catch(NotFoundException e)
		{
			// Don't care
		}
		return false;
	}

	private void transformGraphicsImpl(CtClass clazz) throws Exception
	{
		for(CtConstructor constructor : clazz.getConstructors())
		{
			constructor.insertBefore("gdxdbg.GLThreadVerification.GL_THREAD = java.lang.Thread.currentThread();");
		}
	}

	private boolean isGraphicsImpl(CtClass clazz) throws Exception
	{
		for(CtClass i : clazz.getInterfaces())
		{
			if(i.getName().equals("com.badlogic.gdx.Graphics"))
				return true;
		}
		return false;
	}

	private boolean addGLThreadChecks(CtClass clazz) throws Exception
	{
		boolean modified = false;
		for(CtConstructor c : clazz.getConstructors())
		{
			if(shouldModify(clazz, c))
			{
				addThreadCheck(c);
				modified = true;
			}
		}
		
		for(CtMethod c : clazz.getDeclaredMethods())
		{
			if(shouldModify(clazz, c))
			{
				addThreadCheck(c);
				modified = true;
			}
		}
		return modified;
	}
	
	private void addThreadCheck(CtBehavior c) throws Exception
	{
		c.insertBefore("if(!gdxdbg.GLThreadVerification.isGLThread()) { new RuntimeException(\"Not on the GL thread\").printStackTrace(); }");
	}

	private boolean shouldModify(CtClass clazz, CtBehavior c)
	{
		switch(clazz.getName())
		{
			case "com.badlogic.gdx.scenes.scene2d.ui.Label":
				if(c instanceof CtConstructor)
					return true;
				
				switch(c.getName())
				{
					case "setStyle":
					case "setText":
					case "computePrefSize":
					case "layout":
					case "draw":
					case "getPrefWidth":
					case "getPrefHeight":
					case "setWrap":
					case "setAlignment":
					case "setFontScale":
					case "setFontScaleX":
					case "setFontScaleY":
						return true;
				}
				break;
			case "com/badlogic/gdx/scenes/scene2d/ui/TextField":
				if(c instanceof CtConstructor)
					return true;
				
				switch(c.getName())
				{
					case "letterUnderCursor":
					case "wordUnderCursor":
					case "setStyle":
					case "calculateOffsets":
					case "draw":
					case "getTextY":
					case "drawSelection":
					case "drawText":
					case "drawMessageText":
					case "drawCursor":
					case "updateDisplayText":
					case "copy":
					case "cut":
					case "paste":
					case "insert":
					case "delete":
					case "next":
					case "findNextTextField":
					case "setTextFieldListener":
					case "setTextFieldFilter":
					case "setFocusTraversal":
					case "setMessageText":
					case "appendText":
					case "setText":
					case "changeText":
					case "getSelection":
					case "setSelection":
					case "selectAll":
					case "clearSelection":
					case "setCursorPosition":
					case "setOnscreenKeyboard":
					case "setClipboard":
					case "getPrefHeight":
					case "setAlignment":
					case "setPasswordMode":
					case "setPasswordCharacter":
					case "moveCursor":
					case "continueCursor":
						return true;
				}
				break;
				
		}
		
		for(Object annotation : c.getAvailableAnnotations())
		{
			if(annotation.toString().endsWith(".RequireGLThread"))
				return true;
		}
		
		return false;
	}

	public static boolean isGLThread()
	{
		return GL_THREAD == Thread.currentThread();
	}
}
