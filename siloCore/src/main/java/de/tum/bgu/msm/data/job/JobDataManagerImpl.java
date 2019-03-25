/*
 * Copyright  2005 PB Consult Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package de.tum.bgu.msm.data.job;


import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.pb.common.datafile.TableDataSet;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.accessibility.Accessibility;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.events.IssueCounter;
import de.tum.bgu.msm.io.output.DefaultJobWriter;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.simulator.UpdateListener;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Keeps data of dwellings and non-residential floorspace
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 22 February 2013 in Rhede
 **/
public class JobDataManagerImpl implements UpdateListener, JobDataManager {
    
    private final static Logger logger = Logger.getLogger(JobDataManagerImpl.class);

    private final GeoData geoData;
    private final Properties properties;
    private final JobFactory jobFactory;

    private final JobData jobData;
    private final TravelTimes travelTimes;
    private final Accessibility accessibility;

    private int highestJobIdInUse;
    private int[][] vacantJobsByRegion;
    private int[] vacantJobsByRegionPos;
    private int numberOfStoredVacantJobs;
    private final Map<Integer, Double> zonalJobDensity;

    private final Map<Integer, Map<Integer,Map<String,Float>>> jobsByYearByZoneByIndustry = new ConcurrentHashMap<>();

    public JobDataManagerImpl(Properties properties,
                              JobFactory jobFactory, JobData jobData, GeoData geoData,
                              TravelTimes travelTimes, Accessibility accessibility) {
        this.geoData = geoData;
        this.properties = properties;
        this.jobFactory = jobFactory;
        this.jobData = jobData;
        this.travelTimes = travelTimes;
        this.accessibility = accessibility;
        this.zonalJobDensity = new HashMap<>();
    }

    @Override
    public void setup() {
        identifyHighestJobId();
        calculateEmploymentForecast();
        identifyVacantJobs();
        calculateJobDensityByZone();
    }

    @Override
    public void prepareYear(int year) {
        calculateJobDensityByZone();
    }

    @Override
    public void endYear(int year) {}

    @Override
    public void endSimulation() {
        String filejj = properties.main.baseDirectory
                + properties.jobData.jobsFinalFileName
                + "_"
                + properties.main.endYear + ".csv";
        new DefaultJobWriter(this).writeJobs(filejj);
    }

    @Override
    public Job getJobFromId(int jobId) {
        return jobData.get(jobId);
    }

    @Override
    public Collection<Job> getJobs() {
        return jobData.getJobs();
    }
    
    @Override
    public void removeJob(int id) {
        jobData.removeJob(id);
    }

    private void identifyHighestJobId() {
        highestJobIdInUse = 0;
        for (Job job: jobData.getJobs()) {
            highestJobIdInUse = Math.max(highestJobIdInUse, job.getId());
        }
    }

    @Override
    public int getNextJobId() {
        // increase highestJobIdInUse by 1 and return value
        return ++highestJobIdInUse;
    }

    @Override
    public List<Integer> getNextJobIds(int amount) {
        // increase highestJobIdInUse by 1 and return value
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            ids.add(++highestJobIdInUse);
        }
        return ids;
    }

    private void calculateEmploymentForecast() {
        if (properties.jobData.jobForecastMethod.equalsIgnoreCase("interpolate")) {
            interpolateEmploymentForecast();
            logger.info("Forecasted jobs from employment forecast file");
        } else if (properties.jobData.jobForecastMethod.equalsIgnoreCase("rate")){
            calculateEmploymentForecastWithRate();
            logger.info("Forecasted jobs with growth rate");
        }

    }

    private void calculateEmploymentForecastWithRate() {
        int year = properties.main.startYear;
        Map<Integer, Map<String, Float>> jobCountBaseyear = new HashMap<>();
        jobsByYearByZoneByIndustry.put(year, jobCountBaseyear);
        //initialize maps with count = 0
        for (Zone zone : geoData.getZones().values()){
            Map<String, Float> jobsInThisZone = new HashMap<>();
            jobCountBaseyear.put(zone.getZoneId(), jobsInThisZone);
            for (String jobType : JobType.getJobTypes()){
                jobsInThisZone.put(jobType, 0.f);
            }
        }
        //count jobs in SP of base year
        for (Job job : jobData.getJobs()){
            int zoneId = job.getZoneId();
            String jobType = job.getType();
            jobCountBaseyear.get(zoneId).put(jobType, jobCountBaseyear.get(zoneId).get(jobType) + 1);
        }
        logger.info("Count of jobs in synthetic population of the base year completed");
        //forecast the following years
        year++;
        while (year <= properties.main.endYear){
            Map<Integer, Map<String, Float>> jobCountThisyear = new HashMap<>();
            jobsByYearByZoneByIndustry.put(year, jobCountThisyear);
            for (int zone : geoData.getZones().keySet()) {
                Map<String, Float> jobCountThisZone = new HashMap<>();
                for (String jobType : JobType.getJobTypes()){
                    jobCountThisZone.put(jobType, (float)(jobCountBaseyear.get(zone).get(jobType)*
                            Math.pow(1+properties.jobData.growthRateInPercentByJobType.get(jobType)/100,year - properties.main.startYear)));
                }
                jobCountThisyear.put(zone, jobCountThisZone);
            }
            year++;
        }
    }

    private void interpolateEmploymentForecast(){

        TableDataSet jobs;
        try {
            final String filename = properties.main.baseDirectory + "/" + properties.jobData.jobControlTotalsFileName;
            jobs = SiloUtil.readCSVfile(filename);
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
        jobs.buildIndex(jobs.getColumnPosition("SMZ"));
        new JobType(properties.jobData.jobTypes);

        //read the headers
        String[] labels = jobs.getColumnLabels();
        String[] jobTypes = JobType.getJobTypes();
        List<String> years = new ArrayList<>();

        //find the years that are defined in the job forecast
        String jobTypeName = jobTypes[0];
        for (String label : labels) {
            if (label.contains(jobTypeName)) {
                String year = (label.substring(jobTypeName.length(), label.length()));
                if (!years.contains(year)) {
                    years.add(year);
                }
            }
        }
        //proof the rest of job types are in the file
        for (int i = 1; i < jobTypes.length; i++) {
            for (String year : years) {
                boolean found = false;
                for (String label : labels) {
                    if (label.equals(jobTypes[i] + year)) {
                        found = true;
                    }
                }
                if (!found) {
                    throw new RuntimeException("Not defined all job types for year " + year);
                }
            }
        }

        String[] yearsGiven = years.toArray(new String[0]);

        String dir = properties.main.baseDirectory + "scenOutput/" + properties.main.scenarioName + "/employmentForecast/";
        SiloUtil.createDirectoryIfNotExistingYet(dir);

            int previousFixedYear = Integer.parseInt(yearsGiven[0]);
            int nextFixedYear;
            int interpolatedYear = previousFixedYear;
            for (int i = 0; i < yearsGiven.length - 1; i++) {
                nextFixedYear = Integer.parseInt(yearsGiven[i + 1]);
                while (interpolatedYear <= nextFixedYear) {
                    Map<Integer, Map<String, Float>> jobsThisyear = new HashMap<>();
                    jobsByYearByZoneByIndustry.put(2000 + interpolatedYear, jobsThisyear);
                    final String forecastFileName = dir + properties.jobData.employmentForeCastFile + (2000 + interpolatedYear) + ".csv";
                    final PrintWriter pw = SiloUtil.openFileForSequentialWriting(forecastFileName, false);
                    final StringBuilder builder = new StringBuilder("zone");
                    for (String jobType : JobType.getJobTypes()) {
                        builder.append(",").append(jobType);
                    }
                    builder.append("\n");
                    for (int zone : geoData.getZones().keySet()) {
                        Map<String, Float> jobsThisZone = new HashMap<>();
                        jobsThisyear.put(zone, jobsThisZone);
                        builder.append(zone);
                        for (int jobTp = 0; jobTp < JobType.getNumberOfJobTypes(); jobTp++) {
                            final int index = jobs.getIndexedRowNumber(zone);
                            float currentValue;
                            if (interpolatedYear == previousFixedYear) {
                                //todo look at a different place if it is the base year!
                                currentValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i]);
                            } else if (interpolatedYear == nextFixedYear) {
                                currentValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i + 1]);
                            } else {
                                final float previousFixedValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i]);
                                final float nextFixedValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i + 1]);
                                currentValue = previousFixedValue + (nextFixedValue - previousFixedValue) * (interpolatedYear - previousFixedYear) /
                                        (nextFixedYear - previousFixedYear);
                            }

                            jobsThisZone.put(JobType.getJobType(jobTp), currentValue);
                            builder.append(",").append(currentValue);
                        }
                        builder.append("\n");
                    }
                    pw.print(builder.toString());
                    pw.close();
                    interpolatedYear++;
                }
                previousFixedYear = nextFixedYear;
            }


    }
    

    @Override
    public float getJobForecast(int year, int zone, String jobType){
        return jobsByYearByZoneByIndustry.get(year).get(zone).get(jobType);
    }

    private void identifyVacantJobs() {
        // identify vacant jobs by region (one-time task at beginning of model run only)
        numberOfStoredVacantJobs = properties.jobData.maxStorageOfvacantJobs;
        int highestRegionID = geoData.getRegions().keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        vacantJobsByRegion = new int[highestRegionID + 1][numberOfStoredVacantJobs + 1];
        vacantJobsByRegionPos = new int[highestRegionID + 1];

        logger.info("  Identifying vacant jobs");
        for (Job jj : jobData.getJobs()) {
            if (jj.getWorkerId() == -1) {
                int jobId = jj.getId();

                int region = geoData.getZones().get(jj.getZoneId()).getRegion().getId();
                if (vacantJobsByRegionPos[region] < numberOfStoredVacantJobs) {
                    vacantJobsByRegion[region][vacantJobsByRegionPos[region]] = jobId;
                    vacantJobsByRegionPos[region]++;
                } else {
                    IssueCounter.countExcessOfVacantJobs(region);
                }
                if (jobId == SiloUtil.trackJj) {
                    SiloUtil.trackWriter.println("Added job " + jobId + " to list of vacant jobs.");
                }
            }
        }
    }

    @Override
    public void quitJob(boolean makeJobAvailableToOthers, Person person) {
        // Person quits job and the job is added to the vacantJobList
        // <makeJobAvailableToOthers> is false if this job disappears from the job market
        if (person == null) {
            return;
        }
        final int workplace = person.getJobId();
        Job jb = jobData.get(workplace);
        if (makeJobAvailableToOthers) {
            addJobToVacancyList(jb.getZoneId(), workplace);
        }
        jb.setWorkerID(-1);
        person.setWorkplace(-1);
        person.setOccupation(Occupation.UNEMPLOYED);
        person.setIncome((int) (person.getIncome() * 0.6 + 0.5));
        //todo: think about smarter retirement/social welfare algorithm to adjust income after employee leaves work.
    }
    
    @Override
    public int getNumberOfVacantJobsByRegion(int region) {
        return vacantJobsByRegionPos[region];
    }
    
    @Override
    public int findVacantJob(Zone homeZone, Collection<Region> regions) {
        // select vacant job for person living in homeZone

        Map<Region, Double> regionProb = new HashMap<>();

        if (homeZone != null) {
            // person has home location (i.e., is not inmigrating right now)
            for (Region reg : regions) {
                if (vacantJobsByRegionPos[reg.getId()] > 0) {
                    int distance = (int) (travelTimes.getTravelTimeToRegion(homeZone, reg,
                    		properties.transportModel.peakHour_s, TransportMode.car) + 0.5);
                    regionProb.put(reg, accessibility.getCommutingTimeProbability(distance) * (double) getNumberOfVacantJobsByRegion(reg.getId()));
                }
            }
            if (SiloUtil.getSum(regionProb.values()) == 0) {
                // could not find job in reasonable distance. Person will have to commute far and is likely to relocate in the future
                for (Region reg : regions) {
                    if (vacantJobsByRegionPos[reg.getId()] > 0) {
                    	int distance = (int) (travelTimes.getTravelTimeToRegion(homeZone, reg,
                                properties.transportModel.peakHour_s, TransportMode.car) + 0.5);
                    	regionProb.put(reg, 1. / distance);
                    }
                }
            }
        } else {
            // person has no home location because (s)he is inmigrating right now and a dwelling has not been chosen yet
            for (Region reg : regions) {
                if (vacantJobsByRegionPos[reg.getId()] > 0) {
                	regionProb.put(reg, (double) getNumberOfVacantJobsByRegion(reg.getId()));
                }
            }
        }

        if (SiloUtil.getSum(regionProb.values()) == 0) {
            logger.warn("No jobs remaining. Could not find new job.");
            return -1;
        }
        int selectedRegion = SiloUtil.select(regionProb).getId();
        if (getNumberOfVacantJobsByRegion(selectedRegion) == 0) {
            logger.warn("Selected region " + selectedRegion + " but could not find any jobs there.");
            return -1;
        }
        float[] jobProbability = new float[getNumberOfVacantJobsByRegion(selectedRegion)];
        jobProbability = SiloUtil.setArrayToValue(jobProbability, 1);
        int selectedJob = SiloUtil.select(jobProbability);

        int jobId = vacantJobsByRegion[selectedRegion][selectedJob];
        vacantJobsByRegion[selectedRegion][selectedJob] = vacantJobsByRegion[selectedRegion][vacantJobsByRegionPos[selectedRegion] - 1];
        vacantJobsByRegion[selectedRegion][vacantJobsByRegionPos[selectedRegion] - 1] = 0;
        vacantJobsByRegionPos[selectedRegion] -= 1;
        if (jobId == SiloUtil.trackJj)
            SiloUtil.trackWriter.println("Removed job " + jobId + " from list of vacant jobs.");
        return jobId;
    }


    @Override
    public void addJobToVacancyList(int zone, int jobId) {
        // add job jobId to vacancy list

        int region = geoData.getZones().get(zone).getRegion().getId();
        vacantJobsByRegion[region][vacantJobsByRegionPos[region]] = jobId;
        if (vacantJobsByRegionPos[region] < numberOfStoredVacantJobs) {
            vacantJobsByRegionPos[region]++;
        }
        if (vacantJobsByRegionPos[region] >= numberOfStoredVacantJobs) {
            IssueCounter.countExcessOfVacantJobs(region);
        }
        if (jobId == SiloUtil.trackJj) {
            SiloUtil.trackWriter.println("Added job " + jobId + " to list of vacant jobs.");
        }
    }


    public void summarizeJobs(Map<Integer, Region> regions) {
        // summarize jobs for summary file

        String txt = "jobByRegion";
        for (String empType : JobType.getJobTypes()) txt += "," + empType;
        SummarizeData.resultFile(txt + ",total");

        final int highestId = regions.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        int[][] jobsByTypeAndRegion = new int[JobType.getNumberOfJobTypes()][highestId + 1];
        for (Job job : jobData.getJobs()) {
            jobsByTypeAndRegion[JobType.getOrdinal(job.getType())][geoData.getZones().get(job.getZoneId()).getRegion().getId()]++;
        }

        for (int region : regions.keySet()) {
            StringBuilder line = new StringBuilder(String.valueOf(region));
            int regionSum = 0;
            for (String empType : JobType.getJobTypes()) {
                line.append(",").append(jobsByTypeAndRegion[JobType.getOrdinal(empType)][region]);
                regionSum += jobsByTypeAndRegion[JobType.getOrdinal(empType)][region];
            }
            SummarizeData.resultFile(line + "," + regionSum);
        }
    }


    private void calculateJobDensityByZone() {
        final Multiset<Integer> counter = ConcurrentHashMultiset.create();
        jobData.getJobs().parallelStream().forEach(j -> counter.add(j.getZoneId()));
        geoData.getZones().forEach((id, zone) -> zonalJobDensity.put(id, (double) (counter.count(id) / zone.getArea_sqmi())));
    }


    @Override
    public double getJobDensityInZone(int zone) {
        return zonalJobDensity.get(zone);
    }

    @Override
    public int getJobDensityCategoryOfZone(int zone) {
        // return job density category 1 to 10 of zone
        //TODO: magic numbers
        float[] densityCategories = {0.f, 0.143f, 0.437f, 0.865f, 1.324f, 1.8778f, 2.664f, 3.99105f, 6.f, 12.7f};
        for (int i = 0; i < densityCategories.length; i++) {
            if (zonalJobDensity.get(zone) < densityCategories[i]) {
                return i;
            }
        }
        return densityCategories.length;
    }

    @Override
    public void addJob(Job jj) {
        this.jobData.addJob(jj);
    }

    @Override
    public JobFactory getFactory() {
        return jobFactory;
    }
}