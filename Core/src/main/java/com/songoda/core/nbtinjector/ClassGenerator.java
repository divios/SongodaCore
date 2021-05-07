package com.songoda.core.nbtinjector;

import java.io.IOException;
import java.lang.reflect.Constructor;

import com.songoda.core.compatibility.ServerVersion;
import com.songoda.core.nms.nbt.NBTWrapper;
import com.songoda.core.utils.NMSUtils;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

public class ClassGenerator {

	/**
	 * Hidden Constructor
	 */
	private ClassGenerator() {

	}

	private static final String GENERATOR_PACKAGE = "com.songoda.nbtinjector.generated";

	/**
	 * Wrappes a given class with the INBTWrapper interface
	 * 
	 * @param classPool     The Javassist classpool
	 * @param originalClass The NMS Tile/Entity class
	 * @param writeMethod   String of the saving method
	 * @param readMethod    String of the loading method
	 * @return Class reference to the generated class
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 * @throws IOException
	 */
	protected static Class<?> wrapNbtClass(ClassPool classPool, Class<?> originalClass, String writeMethod,
										   String readMethod) throws NotFoundException, CannotCompileException, IOException {

		String clazz = GENERATOR_PACKAGE + "." + originalClass.getSimpleName();

		System.out.println(clazz);
		try {
			return Class.forName(clazz);
		} catch(Exception ignored) {
		}

		classPool.insertClassPath(new LoaderClassPath(ClassGenerator.class.getClassLoader()));

		CtClass generated = classPool.makeClass(clazz);

		CtClass wrapperInterface = classPool.get(NBTWrapper.class.getName());
		generated.setInterfaces(new CtClass[] { wrapperInterface });
		generated.setSuperclass(classPool.get(originalClass.getName()));

		classPool.importPackage("net.minecraft.server." + ServerVersion.getServerVersionString());
		classPool.importPackage(GENERATOR_PACKAGE);

		generated.addField(CtField.make("public NBTTagCompound $extraCompound = new NBTTagCompound();", generated));

		generated.addMethod(CtMethod.make(
				"public NBTTagCompound getNbtData() {\n"
						+ "  return this.$extraCompound;\n"
						+ "}", generated));

		generated.addMethod(CtMethod.make(
				"public NBTTagCompound readExtraCompound(NBTTagCompound root) {\n"
						+ "  NBTTagCompound compound = root.getCompound(\"__extraData\");\n"
						+ "  this.$extraCompound = compound;\n"
						+ "  return root;"
						+ "}", generated));

		generated.addMethod(CtMethod.make(
				"public NBTTagCompound writeExtraCompound(NBTTagCompound root) {\n"
						+ "  NBTBase compound = (NBTBase) this.$extraCompound;\n"
						+ "  NBTTagCompound newRoot = new NBTTagCompound();\n"
						+ "  newRoot.set(\"__extraData\", compound);\n"
						+ "  root.a(newRoot);\n"
						+ "  return root;"
						+ "}", generated));

		generated.addMethod(CtMethod.make(writeMethod, generated));
		generated.addMethod(CtMethod.make(readMethod, generated));

		// Overwrite constructors
		for (Constructor<?> constructor : originalClass.getConstructors()) {
			StringBuilder paramString = new StringBuilder();
			StringBuilder paramNameString = new StringBuilder();
			int c = 0;
			for (Class<?> cl : constructor.getParameterTypes()) {
				if (c != 0) {
					paramString.append(",");
					paramNameString.append(",");
				}
				paramString.append(cl.getName()).append(" param").append(c);
				paramNameString.append("param").append(c);
				c++;
			}
			generated.addConstructor(CtNewConstructor.make("public " + originalClass.getSimpleName() + "(" + paramString
					+ ") {\n" + "  super(" + paramNameString + ");\n" + "}", generated));
		}

		generated.writeFile("nbtinjector_generated");
		return generated.toClass(NBTWrapper.class.getClassLoader(), NBTWrapper.class.getProtectionDomain());
	}

	/**
	 * Creates a class for the EntityTypes getter for a given entity
	 * 
	 * @param classPool   The Javassist classpool
	 * @param targetClass The NMS Entity class
	 * @return Class reference to the generated class
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 * @throws IOException
	 */
	protected static Class<?> createEntityTypeWrapper(ClassPool classPool, Class<?> targetClass)
			throws NotFoundException, CannotCompileException, IOException {
		classPool.insertClassPath(new LoaderClassPath(ClassGenerator.class.getClassLoader()));

		CtClass generated = classPool.makeClass(GENERATOR_PACKAGE + ".entityCreator." + targetClass.getSimpleName());

		CtClass wrapperInterface = classPool.get(NMSUtils.getNMSClass("EntityTypes").getName() + "$b");
		generated.setInterfaces(new CtClass[] { wrapperInterface });

		classPool.importPackage(ServerVersion.getServerPackagePath());
		classPool.importPackage(GENERATOR_PACKAGE);

		generated.addMethod(CtMethod.make("public Entity create(EntityTypes var1, World var2) {\n"
				+ "  return new " + targetClass.getName() + "(var1, var2);\n"
				+ "}", generated));

		generated.writeFile("nbtinjector_generated");
		return generated.toClass(NBTWrapper.class.getClassLoader(), NBTWrapper.class.getProtectionDomain());
	}

	/**
	 * Wraps a given Entity class with the INBTWrapper interface
	 * 
	 * @param classPool     The Javassist classpool
	 * @param originalClass The NMS Entity class
	 * @return Class reference to the generated class
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 * @throws IOException
	 */
	protected static Class<?> wrapEntity(ClassPool classPool, Class<?> originalClass)
			throws NotFoundException, CannotCompileException, IOException {
		String writeReturn = ServerVersion.isServerVersionAbove(ServerVersion.V1_10) ? "NBTTagCompound" : "void";
		String writeName = ServerVersion.isServerVersionAtLeast(ServerVersion.V1_12) ? "save" : "f";
		String readName = ServerVersion.isServerVersionAtLeast(ServerVersion.V1_12) ? "load" : "e";
		if (ServerVersion.isServerVersionBelow(ServerVersion.V1_11)) {
			writeName = "b";
			readName = "f";
		}

		String writeMethod = "public " + writeReturn + " " + writeName + "(NBTTagCompound compound) {\n"
				+ "  super." + writeName + "(compound);\n"
				+ "  compound = writeExtraCompound(compound);\n"
				+ "  " + (!"void".equals(writeReturn) ? "return compound;" : "")
				+ "}";

		String readMethod = "public void " + readName + "(NBTTagCompound compound) {\n"
				+ "  super." + readName + "(compound);\n" +
				"  readExtraCompound(compound);\n"
				+ "}";
		return wrapNbtClass(classPool, originalClass, writeMethod, readMethod);
	}
}
