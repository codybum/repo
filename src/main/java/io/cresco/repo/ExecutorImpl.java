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

        }

        return null;


    }
    @Override
    public MsgEvent executeWATCHDOG(MsgEvent incoming) { return null;}
    @Override
    public MsgEvent executeKPI(MsgEvent incoming) { return null; }

    private MsgEvent repoList(MsgEvent msg) {

        Map<String,List<Map<String,String>>> repoMap = new HashMap<>();
        repoMap.put("plugins",getPluginInventory());

        List<Map<String,String>> contactMap = getNetworkAddresses();
        repoMap.put("server",contactMap);

        msg.setCompressedParam("repolist",gson.toJson(repoMap));
        return msg;

    }

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

    private List<Map<String,String>> getPluginInventory() {
        List<Map<String,String>> pluginFiles = null;
        try
        {
            File repoDir = getRepoDir();
                //File folder = new File(repoPath);
                if (repoDir != null) {
                    pluginFiles = new ArrayList<>();
                    File[] listOfFiles = repoDir.listFiles();

                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile()) {
                            try {
                                String jarPath = listOfFiles[i].getAbsolutePath();
                                String jarFileName = listOfFiles[i].getName();
                                String pluginName = getPluginName(jarPath);
                                String pluginMD5 = getJarMD5(jarPath);
                                String pluginVersion = getPluginVersion(jarPath);
                                //System.out.println(pluginName + " " + jarFileName + " " + pluginVersion + " " + pluginMD5);
                                //pluginFiles.add(listOfFiles[i].getAbsolutePath());
                                Map<String, String> pluginMap = new HashMap<>();
                                pluginMap.put("pluginname", pluginName);
                                pluginMap.put("jarfile", jarFileName);
                                pluginMap.put("md5", pluginMD5);
                                pluginMap.put("version", pluginVersion);
                                pluginFiles.add(pluginMap);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                    if (pluginFiles.isEmpty()) {
                        pluginFiles = null;
                    }
                }
                else {
                    logger.error("No Repo Found!");
                }

        }
        catch(Exception ex)
        {
            pluginFiles = null;
        }
        return pluginFiles;
    }

    private String getPluginName(String jarFile) {
        String version = null;
        try{
            //String jarFile = AgentEngine.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            //logger.debug("JARFILE:" + jarFile);
            //File file = new File(jarFile.substring(5, (jarFile.length() )));
            File file = new File(jarFile);

            boolean calcHash = true;
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileTime = attr.creationTime().toMillis();

            FileInputStream fis = new FileInputStream(file);
            @SuppressWarnings("resource")
            JarInputStream jarStream = new JarInputStream(fis);
            Manifest mf = jarStream.getManifest();

            Attributes mainAttribs = mf.getMainAttributes();
            version = mainAttribs.getValue("Bundle-SymbolicName");
        }
        catch(Exception ex)
        {
            ex.printStackTrace();

        }
        return version;
    }

    private String getPluginVersion(String jarFile) {
        String version = null;
        try{
            //String jarFile = AgentEngine.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            //logger.debug("JARFILE:" + jarFile);
            //File file = new File(jarFile.substring(5, (jarFile.length() )));
            File file = new File(jarFile);

            boolean calcHash = true;
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileTime = attr.creationTime().toMillis();

            FileInputStream fis = new FileInputStream(file);
            @SuppressWarnings("resource")
            JarInputStream jarStream = new JarInputStream(fis);
            Manifest mf = jarStream.getManifest();

            Attributes mainAttribs = mf.getMainAttributes();
            version = mainAttribs.getValue("Bundle-Version");
        }
        catch(Exception ex)
        {
            ex.printStackTrace();

        }
        return version;
    }

    private String getJarMD5(String pluginFile) {
        String jarString = null;
        try
        {
            Path path = Paths.get(pluginFile);
            byte[] data = Files.readAllBytes(path);

            MessageDigest m= MessageDigest.getInstance("MD5");
            m.update(data);
            jarString = new BigInteger(1,m.digest()).toString(16);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return jarString;
    }




}