package org.scriptcraftjs.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngineFactory;
// imports for GraalJS bindings
import javax.script.Bindings;
import javax.script.ScriptContext;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ScriptCraftPlugin extends JavaPlugin
{
    public boolean canary = false;
    public boolean bukkit = true;
    // right now all ops share the same JS context/scope
    // need to look at possibly having context/scope per operator
    //protected Map<CommandSender,ScriptCraftEvaluator> playerContexts = new HashMap<CommandSender,ScriptCraftEvaluator>();
    private String NO_JAVASCRIPT_MESSAGE = "No JavaScript Engine available. ScriptCraft will not work without Javascript.";
    protected ScriptEngine engine = null;

    @Override public void onEnable()
    {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(getClassLoader());
        try {
            this.getLogger().severe("Attempting to get script enigine (JavaScript)");
            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            // This older fix does not work with GraalVM in R21.2.0; in this case
            // it does not return any engine ('engine' is null)
            //ScriptEngineManager scriptEngineManager = new ScriptEngineManager(null);
            this.engine = scriptEngineManager.getEngineByName("Nashorn");
            if (this.engine != null) {
                this.getLogger().severe("Using Oracle Nashorn");
            } else {
                this.engine = scriptEngineManager.getEngineByName("JavaScript");
                if (this.engine != null) {
                    this.getLogger().severe("Using JavaScript");
                } else {
                    this.engine = scriptEngineManager.getEngineByName("Graal.js");
                    if (this.engine != null) {
                        this.getLogger().severe("Graal.js");
                    }
                }
            }

            if (this.engine == null) {
                this.getLogger().severe(NO_JAVASCRIPT_MESSAGE);
                this.getLogger().severe("Available engines include:");
                List<ScriptEngineFactory> engines = (new ScriptEngineManager()).getEngineFactories();
                for (ScriptEngineFactory f: engines) {
                    // System.out.println(f.getLanguageName()+" "+f.getEngineName()+" "+f.getNames().toString());
                    this.getLogger().severe(f.getLanguageName()+" "+f.getEngineName()+" "+f.getNames().toString());
                }
            } else {
                // Enrico, adding bindings to work with GraalJS,
                // see https://www.graalvm.org/reference-manual/js/NashornMigrationGuide/
                Bindings bindings = this.engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("polyglot.js.allowAllAccess", true);
                bindings.put("polyglot.js.nashorn-compat", true);

                Invocable inv = (Invocable) this.engine;
                this.engine.eval(new InputStreamReader(this.getResource("boot.js")));
                inv.invokeFunction("__scboot", this, engine);
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.getLogger().severe(e.getMessage());
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd,
                                      String alias,
                                      String[] args)
    {
        List<String> result = new ArrayList<String>();
        if (this.engine == null) {
            this.getLogger().severe(NO_JAVASCRIPT_MESSAGE);
            return null;
        }
        try {
            Invocable inv = (Invocable)this.engine;
            inv.invokeFunction("__onTabComplete", result, sender, cmd, alias, args);
        } catch (Exception e) {
            sender.sendMessage(e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        boolean result = false;
        Object jsResult = null;
        if (this.engine == null) {
            this.getLogger().severe(NO_JAVASCRIPT_MESSAGE);
            return false;
        }
        try {
            jsResult = ((Invocable)this.engine).invokeFunction("__onCommand", sender, cmd, label, args);
        } catch (Exception se) {
            this.getLogger().severe(se.toString());
            se.printStackTrace();
            sender.sendMessage(se.getMessage());
        }
        if (jsResult != null){
            return ((Boolean)jsResult).booleanValue();
        }
        return result;
    }
}
