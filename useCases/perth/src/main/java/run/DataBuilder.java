package run;

import com.google.common.collect.EnumMultiset;
import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.container.DefaultDataContainer;
import de.tum.bgu.msm.data.accessibility.Accessibility;
import de.tum.bgu.msm.data.dwelling.*;
import de.tum.bgu.msm.data.geo.DefaultGeoData;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.household.*;
import de.tum.bgu.msm.data.job.*;
import de.tum.bgu.msm.data.person.*;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.io.input.DefaultHouseholdReader;
import de.tum.bgu.msm.io.input.DwellingReader;
import de.tum.bgu.msm.io.input.JobReader;
import de.tum.bgu.msm.io.input.PersonReader;
import de.tum.bgu.msm.models.transportModel.matsim.MatsimTravelTimes;
import de.tum.bgu.msm.properties.Properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.tum.bgu.msm.properties.modules.TransportModelPropertiesModule.TransportModelIdentifier.MATSIM;

public final class DataBuilder {

    private DataBuilder() {
    }

    public static DataContainer buildDataContainer(Properties properties) {

        DefaultGeoData geoData = new DefaultGeoData(properties);

        List<DwellingType> dwellingTypeList = new ArrayList<>();
        Collections.addAll(dwellingTypeList, DwellingTypePerth.values());

        DwellingData dwellingData = new DwellingDataImpl();
        HouseholdData householdData = new HouseholdDataImpl();
        JobData jobData = new JobDataImpl();

        TravelTimes travelTimes;
        if (properties.transportModel.transportModelIdentifier == MATSIM) {
            travelTimes = new MatsimTravelTimes();
        } else {
            travelTimes = new SkimTravelTimes();
        }

        Accessibility accessibility = new AccessibilityPerth(geoData, travelTimes, properties, dwellingData, householdData);

        //TODO: revise this!
        new JobType(properties.jobData.jobTypes);

        RealEstateDataManager realEstateManager = new RealEstateDataManagerImpl(
                dwellingTypeList, dwellingData,
                householdData, geoData,
                new DwellingFactoryImpl(),
                properties);

        JobDataManager jobManager = new JobDataManagerImpl(
                properties, new JobFactoryImpl(),
                jobData, geoData,
                travelTimes, accessibility);

        final HouseholdFactory hhFactory = new HouseholdFactoryImpl();
        final PersonFactory ppFactory = new PersonFactoryImpl();

        HouseholdDataManager householdManager = new HouseholdDataManagerImpl(
                householdData, dwellingData, geoData,
                ppFactory, hhFactory,
                properties, realEstateManager);

        DataContainer dataContainer = new DefaultDataContainer(
                geoData, realEstateManager,
                jobManager, householdManager,
                travelTimes, accessibility, properties);
        return dataContainer;
    }

    public static void readInput(Properties properties, DataContainer dataContainer) {
        final GeoDataReaderPerth geoDataReaderMstm = new GeoDataReaderPerth((DefaultGeoData) dataContainer.getGeoData());

        String fileName = properties.main.baseDirectory + properties.geo.zonalDataFile;
        String pathShp = properties.main.baseDirectory + properties.geo.zoneShapeFile;
        geoDataReaderMstm.readZoneCsv(fileName);
        geoDataReaderMstm.readZoneShapefile(pathShp);

        int year = properties.main.startYear;

        readHouseholds(properties, dataContainer.getHouseholdDataManager(),
                 dataContainer.getHouseholdDataManager().getHouseholdFactory(), year);
        readPersons(properties, dataContainer.getHouseholdDataManager(), dataContainer.getHouseholdDataManager().getPersonFactory(), year);
        readDwellings(properties, dataContainer.getRealEstateDataManager(), dataContainer.getGeoData(), year);

        List<Household> toBeRemoved = new ArrayList<>();

        int counterRemoved = 0;
        for(Household household: dataContainer.getHouseholdDataManager().getHouseholds()) {
            for(Person person: household.getPersons().values()) {
                if(person.getHousehold() == null) {
                    System.out.println("j");
                }
            }

            final Dwelling dwelling = dataContainer.getRealEstateDataManager().getDwelling(household.getDwellingId());
            if(dwelling == null) {
                dataContainer.getHouseholdDataManager().removeHousehold(household.getId());
                counterRemoved++;
            } else {
                EnumMultiset<Gender> marriedCounter = EnumMultiset.create(Gender.class);
                for (Person person : household.getPersons().values()) {
                    if(person.getRole() == PersonRole.MARRIED) {
                        marriedCounter.add(person.getGender());
//                        if (HouseholdUtil.findMostLikelyPartner(person, household) == null) {
//                            dwelling.setResidentID(-1);
//                            household.setDwelling(-1);
//                            dataContainer.getHouseholdDataManager().removeHousehold(household.getId());
//                            counterRemoved++;
//                            break;
//                        }
                    }
                }
                if(marriedCounter.count(Gender.MALE)!=marriedCounter.count(Gender.FEMALE)) {
                    dwelling.setResidentID(-1);
                    household.setDwelling(-1);
                    dataContainer.getHouseholdDataManager().removeHousehold(household.getId());
                    counterRemoved++;
                }
            }
        }

        System.out.println(counterRemoved + "households cleaned");

        JobReader jjReader = new JobReaderPerth(dataContainer.getJobDataManager(), dataContainer.getGeoData());
        String jobsFile = properties.main.baseDirectory + properties.jobData.jobsFileName + "_" + year + ".csv";
        jjReader.readData(jobsFile);
    }

    private static void readDwellings(Properties properties, RealEstateDataManager realEstateManager, GeoData geoData, int year) {
        DwellingReader ddReader = new DwellingReaderPerth(realEstateManager, geoData);
        String dwellingsFile = properties.main.baseDirectory + properties.realEstate.dwellingsFileName + "_" + year + ".csv";
        ddReader.readData(dwellingsFile);
    }

    private static void readHouseholds(Properties properties, HouseholdDataManager householdData, HouseholdFactory hhFactory, int year) {
        String householdFile = properties.main.baseDirectory + properties.householdData.householdFileName;
        householdFile += "_" + year + ".csv";
        DefaultHouseholdReader hhReader = new DefaultHouseholdReader(householdData, hhFactory);
        hhReader.readData(householdFile);
    }

    private static void readPersons(Properties properties, HouseholdDataManager householdData, PersonFactory ppFactory, int year) {
        String personFile = properties.main.baseDirectory + properties.householdData.personFileName;
        personFile += "_" + year + ".csv";
        PersonReader personReader = new PersonReaderPerth(householdData);
        personReader.readData(personFile);
    }
}