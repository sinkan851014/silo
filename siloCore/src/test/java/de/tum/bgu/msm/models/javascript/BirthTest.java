//package de.tum.bgu.msm.models.javascript;
//
//import de.tum.bgu.msm.models.demography.birth.DefaultBirthStrategy;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.io.InputStreamReader;
//import java.io.Reader;
//
//public class BirthTest {
//    private DefaultBirthStrategy calculator;
//
//    @Before
//    public void setup() {
//        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("BirthProbabilityCalc"));
//        float localScaler = 0.87f;
//        calculator = new DefaultBirthStrategy(reader, localScaler);
//    }
//
//    @Test
//    public void testModelOne() {
//        float scaler = 0.87f;
//        Assert.assertEquals((12.1/1000.*scaler), calculator.calculateBirthProbability(40,1), 0.);
//    }
//
//    @Test
//    public void testModelTwo() {
//        Assert.assertEquals(0.0, calculator.calculateBirthProbability(200,0), 0.);
//    }
//
//    @Test(expected = RuntimeException.class)
//    public void testModelFailures() {
//        calculator.calculateBirthProbability(-2,0);
//    }
//
//}
