package io.cresco.repo;

import com.google.gson.Gson;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class ExecutorImpl implements Executor {

    private PluginBuilder plugin;
    private CLogger logger;
    private Gson gson;

    public ExecutorImpl(PluginBuilder pluginBuilder) {
        this.plugin = pluginBuilder;
        logger = plugin.getLogger(ExecutorImpl.class.getName(),CLogger.Level.Info);
        gson = new Gson();
    }

    @Override
    public MsgEvent executeCONFIG(MsgEvent incoming) {
        return null;
    }
    @Override
    public MsgEvent executeDISCOVER(MsgEvent incoming) {
        return null;
    }
    @Override
    public MsgEvent executeERROR(MsgEvent incoming) {
        return null;
    }
    @Override
    public MsgEvent executeINFO(MsgEvent incoming) { return null; }
    @Override
    public MsgEvent executeEXEC(MsgEvent incoming) {


        logger.debug("Processing Exec message : " + incoming.getParams());

        switch (incoming.getParam("action")) {

            case "repolist":
                return repoList(incoming);
            case "getjar":
                return getPluginJar(incoming);
        }

        return null;


    }
    @Override
    public MsgEvent executeWATCHDOG(MsgEvent incoming) { return null;}
    @Override
    public MsgEvent executeKPI(MsgEvent incoming) { return null; }

    private MsgEvent repoList(MsgEvent msg) {

        Map<String,List<Map<String,String>>> repoMap = new HashMap<>();
        List<Map<String,String>> pluginInventory = null;
        File repoDir = getRepoDir();
        if(repoDir != null) {
            pluginInventory = plugin.getPluginInventory(repoDir.getAbsolutePath());
        }

        repoMap.put("plugins",pluginInventory);

        List<Map<String,String>> repoInfo = getRepoInfo();
        repoMap.put("server",repoInfo);

        msg.setCompressedParam("repolist",gson.toJson(repoMap));
        return msg;

    }

    private List<Map<String,String>> getRepoInfo() {
        List<Map<String,String>> repoInfo = null;
        try {
            repoInfo = new ArrayList<>();
            Map<String, String> repoMap = new HashMap<>();
            repoMap.put("region",plugin.getRegion());
            repoMap.put("agent",plugin.getAgent());
            repoMap.put("pluginid",plugin.getPluginID());
            repoInfo.add(repoMap);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return repoInfo;
    }

    /*
    private List<Map<String,String>> getNetworkAddresses() {
        List<Map<String,String>> contactMap = null;
        try {
            contactMap = new ArrayList<>();
            String port = plugin.getConfig().getStringParam("port", "3445");
            String protocol = "http";
            String path = "/repository";

            List<InterfaceAddress> interfaceAddressList = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.getDisplayName().startsWith("veth") && !networkInterface.isLoopback() && networkInterface.supportsMulticast() && !networkInterface.isPointToPoint() && !networkInterface.isVirtual()) {
                    logger.debug("Found Network Interface [" + networkInterface.getDisplayName() + "] initialized");
                    interfaceAddressList.addAll(networkInterface.getInterfaceAddresses());
                }
            }

            for (InterfaceAddress inaddr : interfaceAddressList) {
                logger.debug("interface addresses " + inaddr);
                Map<String, String> serverMap = new HashMap<>();
                String hostAddress = inaddr.getAddress().getHostAddress();
                if (hostAddress.contains("%")) {
                    String[] remoteScope = hostAddress.split("%");
                    hostAddress = remoteScope[0];
                }

                serverMap.put("protocol", protocol);
                serverMap.put("ip", hostAddress);
                serverMap.put("port", port);
                serverMap.put("path", path);
                contactMap.add(serverMap);

            }


            //put hostname at top of list
            InetAddress addr = InetAddress.getLocalHost();
            String hostAddress = addr.getHostAddress();
            if (hostAddress.contains("%")) {
                String[] remoteScope = hostAddress.split("%");
                hostAddress = remoteScope[0];
            }
            Map<String,String> serverMap = new HashMap<>();
            serverMap.put("protocol", protocol);
            serverMap.put("ip", hostAddress);
            serverMap.put("port", port);
            serverMap.put("path", path);

            contactMap.remove(contactMap.indexOf(serverMap));
            contactMap.add(0,serverMap);

            //Use env var for host with hidden external addresses
            String externalIp = plugin.getConfig().getStringParam("externalip");
            //externalIp = "128.163.202.50";
            if(externalIp != null) {
                Map<String, String> serverMapExternal = new HashMap<>();
                serverMapExternal.put("protocol", protocol);
                serverMapExternal.put("ip", externalIp);
                serverMapExternal.put("port", port);
                serverMapExternal.put("path", path);
                contactMap.add(0,serverMapExternal);
            }
//test
        } catch (Exception ex) {
            logger.error("getNetworkAddresses ", ex.getMessage());
        }


        return contactMap;
    }
    */

    private File getRepoDir() {
        File repoDir = null;
        try {

            String repoDirString =  plugin.getConfig().getStringParam("repo_dir","repo");

            File tmpRepo = new File(repoDirString);
            if(tmpRepo.isDirectory()) {
                repoDir = tmpRepo;
            }

        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return repoDir;
    }

    private MsgEvent getPluginJar(MsgEvent incoming) {

        try {
            if ((incoming.getParam("action_pluginname") != null) && (incoming.getParam("action_pluginmd5") != null)) {
                String requestPluginName = incoming.getParam("action_pluginname");
                String requestPluginMD5 = incoming.getParam("action_pluginmd5");

                File repoDir = getRepoDir();
                if (repoDir != null) {

                    List<Map<String, String>> pluginInventory = plugin.getPluginInventory(getRepoDir().getAbsolutePath());
                    for (Map<String, String> repoMap : pluginInventory) {

                        if (repoMap.containsKey("pluginname") && repoMap.containsKey("md5") && repoMap.containsKey("jarfile")) {
                            String pluginName = repoMap.get("pluginname");
                            String pluginMD5 = repoMap.get("md5");
                            String pluginJarFile = repoMap.get("jarfile");

                            if (pluginName.equals(requestPluginName) && pluginMD5.equals(requestPluginMD5)) {

                                Path jarPath = Paths.get(repoDir + "/" + pluginJarFile);
                                incoming.setDataParam("jardata", java.nio.file.Files.readAllBytes(jarPath));

                            }
                        }

                    }

                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return incoming;
    }


}