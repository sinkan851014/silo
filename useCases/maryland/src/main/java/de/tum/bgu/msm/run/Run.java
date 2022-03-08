package de.tum.bgu.msm.run;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class Run {
    private final static Logger logger = Logger.getLogger(SiloMatsim.class);
    public static void main(String[] args) {
        String arg = "/Users/cooperhuang/Downloads/ud282-master/silo/useCases/maryland/test/scenarios/annapolis/javaFiles/siloMatsim.properties";
        Config config = ConfigUtils.loadConfig("/Users/cooperhuang/Downloads/ud282-master/silo/useCases/maryland/test/scenarios/annapolis/matsim_input/config.xml");

        SiloMatsim siloMatsim = new SiloMatsim(arg, config);
        siloMatsim.run();
        System.out.println("完成");
    }
}