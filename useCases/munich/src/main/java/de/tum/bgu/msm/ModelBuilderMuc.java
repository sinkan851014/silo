package de.tum.bgu.msm;

import de.tum.bgu.msm.container.ModelContainer;
import de.tum.bgu.msm.matsim.MatsimScenarioAssembler;
import de.tum.bgu.msm.matsim.SimpleMatsimScenarioAssembler;
import de.tum.bgu.msm.mito.MitoMatsimScenarioAssembler;
import de.tum.bgu.msm.models.relocation.moves.RegionProbabilityStrategyImpl;
import de.tum.bgu.msm.schools.DataContainerWithSchools;
import de.tum.bgu.msm.data.dwelling.DwellingFactory;
import de.tum.bgu.msm.data.household.HouseholdFactory;
import de.tum.bgu.msm.data.mito.MitoDataConverterMuc;
import de.tum.bgu.msm.data.person.PersonFactory;
import de.tum.bgu.msm.matsim.MatsimTransportModel;
import de.tum.bgu.msm.matsim.ZoneConnectorManager;
import de.tum.bgu.msm.models.EducationModelMuc;
import de.tum.bgu.msm.models.MarriageModelMuc;
import de.tum.bgu.msm.models.autoOwnership.CreateCarOwnershipModel;
import de.tum.bgu.msm.models.carOwnership.CreateCarOwnershipModelMuc;
import de.tum.bgu.msm.models.carOwnership.UpdateCarOwnershipModelMuc;
import de.tum.bgu.msm.models.demography.birth.BirthModelImpl;
import de.tum.bgu.msm.models.demography.birth.DefaultBirthStrategy;
import de.tum.bgu.msm.models.demography.birthday.BirthdayModel;
import de.tum.bgu.msm.models.demography.birthday.BirthdayModelImpl;
import de.tum.bgu.msm.models.demography.death.DeathModel;
import de.tum.bgu.msm.models.demography.death.DeathModelImpl;
import de.tum.bgu.msm.models.demography.death.DefaultDeathStrategy;
import de.tum.bgu.msm.models.demography.divorce.DefaultDivorceStrategy;
import de.tum.bgu.msm.models.demography.divorce.DivorceModel;
import de.tum.bgu.msm.models.demography.divorce.DivorceModelImpl;
import de.tum.bgu.msm.models.demography.driversLicense.DefaultDriversLicenseStrategy;
import de.tum.bgu.msm.models.demography.driversLicense.DriversLicenseModel;
import de.tum.bgu.msm.models.demography.driversLicense.DriversLicenseModelImpl;
import de.tum.bgu.msm.models.demography.education.EducationModel;
import de.tum.bgu.msm.models.demography.employment.EmploymentModel;
import de.tum.bgu.msm.models.demography.employment.EmploymentModelImpl;
import de.tum.bgu.msm.models.demography.leaveParentalHousehold.DefaultLeaveParentalHouseholdStrategy;
import de.tum.bgu.msm.models.demography.leaveParentalHousehold.LeaveParentHhModel;
import de.tum.bgu.msm.models.demography.leaveParentalHousehold.LeaveParentHhModelImpl;
import de.tum.bgu.msm.models.demography.marriage.DefaultMarriageStrategy;
import de.tum.bgu.msm.models.demography.marriage.MarriageModel;
import de.tum.bgu.msm.models.jobmography.JobMarketUpdate;
import de.tum.bgu.msm.models.jobmography.JobMarketUpdateImpl;
import de.tum.bgu.msm.models.realEstate.construction.*;
import de.tum.bgu.msm.models.realEstate.demolition.DefaultDemolitionStrategy;
import de.tum.bgu.msm.models.realEstate.demolition.DemolitionModel;
import de.tum.bgu.msm.models.realEstate.demolition.DemolitionModelImpl;
import de.tum.bgu.msm.models.realEstate.pricing.DefaultPricingStrategy;
import de.tum.bgu.msm.models.realEstate.pricing.PricingModel;
import de.tum.bgu.msm.models.realEstate.pricing.PricingModelImpl;
import de.tum.bgu.msm.models.realEstate.renovation.DefaultRenovationStrategy;
import de.tum.bgu.msm.models.realEstate.renovation.RenovationModel;
import de.tum.bgu.msm.models.realEstate.renovation.RenovationModelImpl;
import de.tum.bgu.msm.models.relocation.DwellingUtilityStrategyImpl;
import de.tum.bgu.msm.models.relocation.HousingStrategyMuc;
import de.tum.bgu.msm.models.relocation.InOutMigrationMuc;
import de.tum.bgu.msm.models.relocation.RegionUtilityStrategyMucImpl;
import de.tum.bgu.msm.models.relocation.migration.InOutMigration;
import de.tum.bgu.msm.models.relocation.moves.DefaultDwellingProbabilityStrategy;
import de.tum.bgu.msm.models.relocation.moves.DefaultMovesStrategy;
import de.tum.bgu.msm.models.relocation.moves.MovesModelImpl;
import de.tum.bgu.msm.models.transportModel.TransportModel;
import de.tum.bgu.msm.properties.Properties;
import org.matsim.core.config.Config;

public class ModelBuilderMuc {

    public static ModelContainer getModelContainerForMuc(DataContainerWithSchools dataContainer, Properties properties, Config config) {

        PersonFactory ppFactory = dataContainer.getHouseholdDataManager().getPersonFactory();
        HouseholdFactory hhFactory = dataContainer.getHouseholdDataManager().getHouseholdFactory();
        DwellingFactory ddFactory = dataContainer.getRealEstateDataManager().getDwellingFactory();

        final BirthModelImpl birthModel = new BirthModelImpl(dataContainer, ppFactory, properties, new DefaultBirthStrategy());

        BirthdayModel birthdayModel = new BirthdayModelImpl(dataContainer, properties);

        DeathModel deathModel = new DeathModelImpl(dataContainer, properties, new DefaultDeathStrategy());

        MovesModelImpl movesModel = new MovesModelImpl(
                dataContainer, properties,
                new DefaultMovesStrategy(),
                new HousingStrategyMuc(dataContainer,
                        properties,
                        dataContainer.getTravelTimes(), new DefaultDwellingProbabilityStrategy(),
                        new DwellingUtilityStrategyImpl(), new RegionUtilityStrategyMucImpl(), new RegionProbabilityStrategyImpl()));

        CreateCarOwnershipModel carOwnershipModel = new CreateCarOwnershipModelMuc(dataContainer);

        DivorceModel divorceModel = new DivorceModelImpl(
                dataContainer, movesModel, carOwnershipModel, hhFactory,
                properties, new DefaultDivorceStrategy());

        DriversLicenseModel driversLicenseModel = new DriversLicenseModelImpl(dataContainer, properties, new DefaultDriversLicenseStrategy());

        EducationModel educationModel = new EducationModelMuc(dataContainer, properties);

        EmploymentModel employmentModel = new EmploymentModelImpl(dataContainer, properties);

        LeaveParentHhModel leaveParentsModel = new LeaveParentHhModelImpl(dataContainer, movesModel,
                carOwnershipModel, hhFactory, properties, new DefaultLeaveParentalHouseholdStrategy());

        JobMarketUpdate jobMarketUpdateModel = new JobMarketUpdateImpl(dataContainer, properties);

        ConstructionModel construction = new ConstructionModelImpl(dataContainer, ddFactory,
                properties, new DefaultConstructionLocationStrategy(), new DefaultConstructionDemandStrategy());


        PricingModel pricing = new PricingModelImpl(dataContainer, properties, new DefaultPricingStrategy());

        RenovationModel renovation = new RenovationModelImpl(dataContainer, properties, new DefaultRenovationStrategy());

        ConstructionOverwrite constructionOverwrite = new ConstructionOverwriteImpl(dataContainer, ddFactory, properties);

        InOutMigration inOutMigration = new InOutMigrationMuc(dataContainer, employmentModel, movesModel,
                carOwnershipModel, driversLicenseModel, properties);

        DemolitionModel demolition = new DemolitionModelImpl(dataContainer, movesModel,
                inOutMigration, properties, new DefaultDemolitionStrategy());

        MarriageModel marriageModel = new MarriageModelMuc(dataContainer, movesModel, inOutMigration,
                carOwnershipModel, hhFactory, properties, new DefaultMarriageStrategy());


        TransportModel transportModel;
        MatsimScenarioAssembler scenarioAssembler;
        switch (properties.transportModel.transportModelIdentifier) {
            case MITO_MATSIM:
                scenarioAssembler = new MitoMatsimScenarioAssembler(dataContainer, properties, new MitoDataConverterMuc());
                transportModel = new MatsimTransportModel(dataContainer, config, properties, null,
                        ZoneConnectorManager.ZoneConnectorMethod.WEIGHTED_BY_POPULATION, scenarioAssembler);
                break;
            case MATSIM:
                scenarioAssembler = new SimpleMatsimScenarioAssembler(dataContainer, properties);
                transportModel = new MatsimTransportModel(dataContainer, config, properties, null,
                        ZoneConnectorManager.ZoneConnectorMethod.WEIGHTED_BY_POPULATION, scenarioAssembler);
                break;
            case NONE:
            default:
                transportModel = null;
        }

        final ModelContainer modelContainer = new ModelContainer(
                birthModel, birthdayModel,
                deathModel, marriageModel,
                divorceModel, driversLicenseModel,
                educationModel, employmentModel,
                leaveParentsModel, jobMarketUpdateModel,
                construction, demolition, pricing, renovation,
                constructionOverwrite, inOutMigration, movesModel, transportModel);

        modelContainer.registerModelUpdateListener(new UpdateCarOwnershipModelMuc(dataContainer, properties));

        return modelContainer;
    }
}