import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.IntList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Meso {
    private final HashMap<PerformanceRange, Integer> targetMinutes;
    protected int[] maxWeekMinutes;
    protected int maxDays;
    protected int maxMinutesDay;
    private Model model;
    private IntVar[] minutes;
    private IntVar[] methods;
    private IntVar[][] ranges;
    private IntVar trainingMinutes;
    private IntVar distanceRanges;
    private IntVar sumKB, sumGA, sumEB, sumSB, sumK123, sumK45;
    private IntVar distKB, distGA, distEB, distSB, distK123, distK45;
    private IntVar overallDistance;
    protected Double[] mesoIntensity;
    protected Solution plan;

    public Meso(double intensity, HashMap<PerformanceRange, Double> targetPercent, int maxMinutesWeek, int weeklyDays) {
        this.mesoIntensity = new Double[]{0.7, 0.8, 0.9, 1.0};
        this.maxWeekMinutes = Arrays.stream(mesoIntensity).mapToInt(i->(int)(i*maxMinutesWeek*intensity)).toArray();
        this.targetMinutes = new HashMap<PerformanceRange, Integer>();
        targetPercent.forEach((k, v) -> targetMinutes.put(k, (int) (v*Arrays.stream(maxWeekMinutes).sum())));
        this.maxDays = weeklyDays;
        this.maxMinutesDay = 60*10;

        initializeModel();
        defineConstraints();
        solveMonth();
    }

    private void initializeModel() {
        this.model = new Model("training");
        this.minutes = model.intVarArray("minutes",28, 0, maxMinutesDay, false);
        this.methods = model.intVarArray("methods",28,  0, Methods.values().length-1, false);
        this.ranges = model.intVarMatrix("ranges", 28, PerformanceRange.values().length, 0, maxMinutesDay, false);
        this.trainingMinutes = model.intVar("totalMinutes", 0, 28*maxMinutesDay);
        this.overallDistance = model.intVar("distance", 0, 28*maxMinutesDay);
        this.sumKB = model.intVar("sumKB", 0, 1000);
        this.sumGA = model.intVar("sumGA", 0, 1000);
        this.sumEB = model.intVar("sumEB", 0, 1000);
        this.sumSB = model.intVar("sumSB", 0, 1000);
        this.sumK123 = model.intVar("sumK123", 0, 1000);
        this.sumK45 = model.intVar("sumK45", 0, 1000);
        this.distKB = model.intVar("distKB", 0, 1000);
        this.distGA = model.intVar("distGA", 0, 1000);
        this.distEB = model.intVar("distEB", 0, 1000);
        this.distSB = model.intVar("distSB", 0, 1000);
        this.distK123 = model.intVar("distK123", 0, 1000);
        this.distK45 = model.intVar("distK45", 0, 1000);
    }

    private IntVar[] getColumn(IntVar[][] matrix, int colNum){
        IntVar[] column = new IntVar[matrix.length];
        for (int rowNum = 0; rowNum<matrix.length; rowNum++){
            column[rowNum] = matrix[rowNum][colNum];
        }
        return column;
    }
    private IntVar[] getColumn(IntVar[][] matrix, PerformanceRange performanceRange){
        return getColumn(matrix, performanceRange.ordinal());
    }

    public void defineConstraints(){
        // get sum of trainingminutes to maximize them
        model.sum(minutes, "=", trainingMinutes).post();

        // sum of minutes in different performance ranges -> optimize
        model.sum(getColumn(ranges, PerformanceRange.KB), "=", sumKB).post();
        model.sum(getColumn(ranges, PerformanceRange.GA), "=", sumGA).post();
        model.sum(getColumn(ranges, PerformanceRange.EB), "=", sumEB).post();
        model.sum(getColumn(ranges, PerformanceRange.SB), "=", sumSB).post();
        model.sum(getColumn(ranges, PerformanceRange.K123), "=", sumK123).post();
        model.sum(getColumn(ranges, PerformanceRange.K45), "=", sumK45).post();

        // Calculate distance to the target Minutes for each performance ranges
        model.arithm(distKB,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();
        model.arithm(distGA,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();
        model.arithm(distEB,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();
        model.arithm(distSB,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();
        model.arithm(distK123,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();
        model.arithm(distK45,"=" , sumKB.dist(targetMinutes.get(PerformanceRange.KB)).abs().intVar()).post();

        // Minizize this Variable
        model.arithm(overallDistance, "=", distKB.add(distGA, distEB, distSB, distK123, distK45).intVar()).post();

        // CONSTRAINTS for weeks
        for (int week = 0; week <4; week++) {
            int startDay = week*7;
            IntVar[] weekVariable = new IntVar[]{minutes[startDay], minutes[startDay + 1], minutes[startDay + 2], minutes[startDay + 3], minutes[startDay + 4], minutes[startDay + 5], minutes[startDay + 6]};
            // time limit on week trainings
            model.sum(weekVariable, "<=", maxWeekMinutes[week]).post();
            model.sum(weekVariable, ">", maxWeekMinutes[week]-30).post();

            // dont train more than x days in a week
            model.count(0, weekVariable, model.intVar(6-maxDays)).post();
        }

        // Training minutes in 15 minute steps
        for (int i = 0; i<minutes.length; i++){
            model.mod(minutes[i], 15, 0).post();
            model.sum(ranges[i], "=", minutes[i]).post();


            // PAUSE
            model.ifOnlyIf(
                    model.arithm(minutes[i], "=", 0),
                    model.arithm(methods[i], "=", Methods.PAUSE.ordinal())
            );

            model.ifThen(
                    model.arithm(methods[i], "=", Methods.DAUERLEISTUNG.ordinal()),
                    model.and(
                            model.or(
                                model.and(
                                        model.arithm(ranges[i][PerformanceRange.KB.ordinal()], ">", 60),
                                        model.arithm(ranges[i][PerformanceRange.KB.ordinal()], "<", 60*4),
                                        model.arithm(ranges[i][PerformanceRange.GA.ordinal()], "=", 0)

                                        ),
                                model.and(
                                        model.arithm(ranges[i][PerformanceRange.GA.ordinal()], ">", 60),
                                        model.arithm(ranges[i][PerformanceRange.GA.ordinal()], "<", 60*4),
                                        model.arithm(ranges[i][PerformanceRange.KB.ordinal()], "=", 0)

                                        )
                            ),
                            model.arithm(ranges[i][PerformanceRange.EB.ordinal()], "=", 0),
                            model.arithm(ranges[i][PerformanceRange.SB.ordinal()], "=", 0),
                            model.arithm(ranges[i][PerformanceRange.K123.ordinal()], "=", 0),
                            model.arithm(ranges[i][PerformanceRange.K45.ordinal()], "=", 0)
                    )
            );

            model.ifThen(
                    model.arithm(methods[i], "=", Methods.INTERVALL.ordinal()),
                    model.arithm(minutes[i], ">", 30)
            );

            model.ifThen(
                    model.arithm(methods[i], "=", Methods.WIEDERHOLUNG.ordinal()),
                    model.arithm(minutes[i], ">", 15)
            );


        }
    }

    private void solveMonth() {
        model.setObjective(Model.MINIMIZE, overallDistance);
        Solver solver = model.getSolver();
        plan = solver.findSolution();
        System.out.println(plan);
    }

    public Session[] getSessionsMonth(){
        Session[] sessions = new Session[28];
        for (int i = 0; i < 28; i++) {
            HashMap dis = new HashMap();
            dis.put(PerformanceRange.KB, ranges[i][0].getValue());
            dis.put(PerformanceRange.GA, ranges[i][1].getValue());
            dis.put(PerformanceRange.EB, ranges[i][2].getValue());
            dis.put(PerformanceRange.SB, ranges[i][3].getValue());
            dis.put(PerformanceRange.K123, ranges[i][4].getValue());
            dis.put(PerformanceRange.K45, ranges[i][5].getValue());
            sessions[i] = new Session(minutes[i].getValue(), Methods.values()[methods[i].getValue()], dis);
        }
        return sessions;
    }

    @Override
    public String toString() {
        return "Meso{" +
                "ranges=" + ranges +
                ", maxWeekMinutes=" + Arrays.toString(maxWeekMinutes) +
                ", maxDays=" + maxDays +
                ", maxMinutesDay=" + maxMinutesDay +
                '}';
    }
}

