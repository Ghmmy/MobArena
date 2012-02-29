package com.garbagemule.MobArena.waves.ability;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.garbagemule.MobArena.Messenger;
import com.garbagemule.MobArena.util.FileUtils;

public class AbilityManager
{
    private static final String jarpath = "." + File.separator + "plugins" + File.separator + "MobArena.jar";
    private static final String classpath = jarpath + ";" + System.getProperty("java.class.path");
    
    private static Map<String,Ability> abilities;
    
    /**
     * Get an Ability by one of its aliases.
     * @param name an Ability alias
     * @return an Ability, if one exists with the given alias, false otherwise
     */
    public static Ability fromString(String name) {
        return abilities.get(name.toLowerCase().replaceAll("[-_.]", ""));
    }
    
    /**
     * Load the known abilities as well as all custom abilities from
     * the specified directory.
     * @param dir a directory of .class (and/or .java) files
     */
    public static void loadAbilities(File classDir) {
        abilities = new HashMap<String,Ability>();
        
        // Extract all of the source files.
        File javaDir = new File(classDir, "src");
        extractSourceFiles(javaDir);
        
        // The custom abilities to compile will be in the 'src'-dir
        compileAbilities(javaDir, classDir);
        
        // Load all the custom abilities.
        loadClasses(classDir);
    }
    
    private static void extractSourceFiles(File javaDir) {
        // Only extract the files if the folder doesn't exist.
        if (javaDir.exists()) return;
        
        String path = "abilities/";
        
        List<String> files = new ArrayList<String>();

        files.add(path + "ChainLightning.java");
        files.add(path + "LivingBomb.java");
        files.add(path + "ObsidianBomb.java");
        files.add(path + "Flood.java");
        files.add(path + "WarpToPlayer.java");
        files.add(path + "RootTarget.java");
        files.add(path + "ShufflePositions.java");

        files.add(path + "ShootArrow.java");
        files.add(path + "ShootFireball.java");
        
        files.add(path + "FireAura.java");
        files.add(path + "LightningAura.java");
        
        files.add(path + "DisorientDistant.java");
        files.add(path + "DisorientNearby.java");
        files.add(path + "DisorientTarget.java");
        
        files.add(path + "PullDistant.java");
        files.add(path + "PullNearby.java");
        files.add(path + "PullTarget.java");
        
        files.add(path + "ThrowDistant.java");
        files.add(path + "ThrowNearby.java");
        files.add(path + "ThrowTarget.java");
        
        FileUtils.extractResources(javaDir, files.toArray(new String[0]));
    }
    
    private static void compileAbilities(File javaDir, File classDir) {
        if (!javaDir.exists()) return;
        
        // Make ready a new list of files to compile.
        List<File> toCompile = getSourceFilesToCompile(javaDir, classDir);
        
        // No files to compile?
        if (toCompile.isEmpty()) {
            return;
        }
        
        // Get the compiler and the file manager
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        
        // Generate some JavaFileObjects
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(toCompile);
        
        // Include the MobArena.jar on the classpath, and set the destination folder.
        List<String> options = Arrays.asList("-classpath", classpath, "-d", classDir.getPath());
        
        // Set up the compilation task.
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
        
        // Call the task.
        task.call();
        
        // And close the file manager.
        try {
            fileManager.close();
        }
        catch (Exception e) {}
    }
    
    private static List<File> getSourceFilesToCompile(File javaDir, File classDir) {
        List<File> result = new ArrayList<File>();
        
        if (javaDir == null || !javaDir.exists()) {
            return result;
        }
        
        // Grab the array of compiled files.
        File[] classFiles = classDir.listFiles();
        
        // Go through each source file.
        for (File javaFile : javaDir.listFiles()) {
            // Skip if it's not a .java file.
            if (!javaFile.getName().endsWith(".java")) {
                Messenger.info("Found invalid ability file: " + javaFile.getName());
                continue;
            }
            
            // Find the associated .class file.
            File classFile = findClassFile(javaFile, classFiles);
            
            // If the .class file is newer, we don't need to compile.
            if (isClassFileNewer(javaFile, classFile)) {
                continue;
            }
            result.add(javaFile);
        }
        
        return result;
    }
    
    private static File findClassFile(File javaFile, File[] classFiles) {
        String javaFileName = javaFile.getName();
        String classFileName = javaFileName.substring(0, javaFileName.lastIndexOf(".")) + ".class";
        
        for (File classFile : classFiles) {
            if (classFile.getName().equals(classFileName)) {
                return classFile;
            }
        }
        return null;
    }
    
    private static boolean isClassFileNewer(File javaFile, File classFile) {
        if (classFile == null) return false;
        
        return (classFile.lastModified() > javaFile.lastModified());
    }
    
    /**
     * (Compiles and) loads all custom abilities in the given directory.
     * @param dir a directory
     */
    private static void loadClasses(File classDir) {
        // Grab the class loader
        ClassLoader loader = getLoader(classDir);
        if (loader == null) return;
        
        StringBuffer buffy = new StringBuffer();
        
        for (File file : classDir.listFiles()) {
            String filename = file.getName();
            
            // Only load .class files.
            int dot = filename.lastIndexOf(".class");
            if (dot < 0) continue;

            // Trim off the .class extension
            String name = filename.substring(0, file.getName().lastIndexOf("."));
            
            // And make an Ability.
            Ability ability = makeAbility(loader, name);
            if (ability == null) continue;
            
            // Then load the ability into the map.
            String abilityName = loadAbility(ability);
            
            if (abilityName != null) {
                buffy.append(", " + abilityName);
            }
        }
    }
    
    /**
     * Loads an Ability into the abilities map by all of its aliases.
     * @param ability an Ability
     * @return the first alias of 
     */
    private static String loadAbility(Ability ability) {
        // Grab the annotation.
        AbilityInfo info = ability.getClass().getAnnotation(AbilityInfo.class);
        if (info == null) return null;
        
        // Put the command in the map with each of its aliases.
        for (String name : info.aliases()) {
            abilities.put(name, ability);
        }
        
        return info.aliases()[0];
    }
    
    /**
     * Ask the given ClassLoader to load the ability with the given name.
     * @param loader a ClassLoader
     * @param name a class name
     * @return an Ability, if the ClassLoader found one with the given name, null otherwise
     */
    private static Ability makeAbility(ClassLoader loader, String name) {
        try {
            // Load the class.
            Class<?> c = loader.loadClass(name);
            
            // Create an instance of it.
            Object o = c.newInstance();
            
            // If it's an ability, return it.
            if (o instanceof Ability) {
                return (Ability) o;
            }
        }
        catch (Exception e) {}
        
        // Otherwise, return null.
        return null;
    }
    
    /**
     * Get a ClassLoader for the given directory.
     * @param dir a directory
     * @return a ClassLoader, or null
     */
    private static ClassLoader getLoader(File dir) {
        try {
            ClassLoader loader = new URLClassLoader(new URL[] { dir.toURI().toURL() }, Ability.class.getClassLoader());
            return loader;
        }
        catch (Exception e) {}
        
        return null;
    }
    
    /**
     * Turn an array of aliases into a comma-separated string of aliases.
     * @param aliases an array of Strings
     * @return a comma-separated String
     */
    /*private static String aliasString(String[] aliases) {
        StringBuffer buffy = new StringBuffer();
        for (String a : aliases) {
            buffy.append(a + ", ");
        }
        return buffy.substring(0, buffy.length() - 2);
    }*/
}
