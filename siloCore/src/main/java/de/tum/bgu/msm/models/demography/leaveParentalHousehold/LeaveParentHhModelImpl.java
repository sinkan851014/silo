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
package de.tum.bgu.msm.models.demography.leaveParentalHousehold;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdDataManager;
import de.tum.bgu.msm.data.household.HouseholdFactory;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.data.person.PersonRole;
import de.tum.bgu.msm.events.impls.person.LeaveParentsEvent;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.autoOwnership.CreateCarOwnershipModel;
import de.tum.bgu.msm.models.relocation.moves.MovesModelImpl;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Simulates children that leave the parental household
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 30 December 2009 in Cologne
 **/
public class LeaveParentHhModelImpl extends AbstractModel implements LeaveParentHhModel {

    private final static Logger logger = Logger.getLogger(LeaveParentHhModelImpl.class);

    private final CreateCarOwnershipModel createCarOwnershipModel;
    private final HouseholdFactory hhFactory;
    private final MovesModelImpl movesModel;
    private HouseholdDataManager householdDataManager;
    private final LeaveParentalHouseholdStrategy strategy;
    private int lackOfDwellingFailedLeavingChild;


    public LeaveParentHhModelImpl(DataContainer dataContainer, MovesModelImpl move,
                                  CreateCarOwnershipModel createCarOwnershipModel, HouseholdFactory hhFactory,
                                  Properties properties, LeaveParentalHouseholdStrategy strategy) {
        super(dataContainer, properties);
        this.movesModel = move;
        this.createCarOwnershipModel = createCarOwnershipModel;
        this.hhFactory = hhFactory;
        this.householdDataManager = dataContainer.getHouseholdDataManager();
        this.strategy = strategy;
    }

    @Override
    public void setup() {
//        switch (properties.main.implementation) {
//            case MARYLAND:
//            case AUSTIN:
//                reader = new InputStreamReader(this.getClass().getResourceAsStream("LeaveParentHhCalcMstm"));
//                break;
//            case MUNICH:
//            case PERTH:
//            case KAGAWA:
//            case CAPE_TOWN:
//                reader = new InputStreamReader(this.getClass().getResourceAsStream("LeaveParentHhCalc"));
//        }
    }

    @Override
    public void prepareYear(int year) {
        lackOfDwellingFailedLeavingChild = 0;
    }

    @Override
    public Collection<LeaveParentsEvent> getEventsForCurrentYear(int year) {
        final List<LeaveParentsEvent> events = new ArrayList<>();
        for (Person person : dataContainer.getHouseholdDataManager().getPersons()) {
            if (qualifiesForParentalHHLeave(person)) {
                events.add(new LeaveParentsEvent(person.getId()));
            }
        }
        return events;
    }

    @Override
    public boolean handleEvent(LeaveParentsEvent event) {
        final Person per = householdDataManager.getPersonFromId(event.getPersonId());
        if (per != null && qualifiesForParentalHHLeave(per)) {
            final double prob = strategy.calculateLeaveParentsProbability(per);
            if (SiloUtil.getRandomNumberAsDouble() < prob) {
                return leaveHousehold(per);
            }
        }
        return false;
    }

    @Override
    public void endYear(int year) {
        if (lackOfDwellingFailedLeavingChild > 0) {
            logger.warn("  Encountered " + lackOfDwellingFailedLeavingChild + " cases " +
                    "where child wanted to leave parental household but could not find vacant dwelling.");
        }

    }

    @Override
    public void endSimulation() {

    }

    boolean leaveHousehold(Person per) {
        // search if dwelling is available
        Household fakeHypotheticalHousehold = hhFactory.createHousehold(-1, -1, 0);
        fakeHypotheticalHousehold.addPerson(per);
        final int newDwellingId = movesModel.searchForNewDwelling(fakeHypotheticalHousehold);
        if (newDwellingId < 0) {
            if (per.getId() == SiloUtil.trackPp || per.getHousehold().getId() == SiloUtil.trackHh) {
                SiloUtil.trackWriter.println(
                        "Person " + per.getId() + " wanted to but could not leave parental Household "
                                + per.getHousehold().getId() + " because no appropriate vacant dwelling was found.");
            }
            lackOfDwellingFailedLeavingChild++;

            if (false) {
                System.out.println("Person " + per.getId() + " could not leave parental household " + per.getHousehold().getId());
                System.out.println("Age/Sex " + per.getAge() + "/" + per.getGender());
                System.out.println("Employment " + per.getOccupation() + " at workplace " + per.getJobId());
                System.out.println("Type " + per.getType() + " and role " + per.getRole());
                System.out.println("Vacant dwellings:");
                final GeoData geoData = dataContainer.getGeoData();
                RealEstateDataManager realEstateDataManager = dataContainer.getRealEstateDataManager();
                for (Region region : geoData.getRegions().values())
                    System.out.println(region + ": " +
                            realEstateDataManager.getNumberOfVacantDDinRegion(region.getId()) + " from Array: " +
                            realEstateDataManager.getListOfVacantDwellingsInRegion(region.getId()).size());
                System.exit(0);
            }

            return false;
        }

        final HouseholdDataManager households = dataContainer.getHouseholdDataManager();
        final Household hhOfThisPerson = households.getHouseholdFromId(per.getHousehold().getId());
        dataContainer.getHouseholdDataManager().saveHouseholdMemento(hhOfThisPerson);
        households.removePersonFromHousehold(per);

        final int newHhId = households.getNextHouseholdId();
        final Household newHousehold = hhFactory.createHousehold(newHhId, -1, 0);
        dataContainer.getHouseholdDataManager().addHousehold(newHousehold);
        households.addPersonToHousehold(per, newHousehold);
        per.setRole(PersonRole.SINGLE);

        movesModel.moveHousehold(newHousehold, -1, newDwellingId);
        if (createCarOwnershipModel != null) {
            createCarOwnershipModel.simulateCarOwnership(newHousehold);
        }

        if (per.getId() == SiloUtil.trackPp || hhOfThisPerson.getId() == SiloUtil.trackHh ||
                newHousehold.getId() == SiloUtil.trackHh) {
            SiloUtil.trackWriter.println("Person " + per.getId() +
                    " has left the parental newHousehold " + hhOfThisPerson.getId() +
                    " and established the new newHousehold " + newHhId + ".");
        }
        return true;
    }

    private boolean qualifiesForParentalHHLeave(Person person) {
        return (householdDataManager.getHouseholdFromId(person.getHousehold().getId()).getHhSize() >= 2 && person.getRole() == PersonRole.CHILD);
    }
}
