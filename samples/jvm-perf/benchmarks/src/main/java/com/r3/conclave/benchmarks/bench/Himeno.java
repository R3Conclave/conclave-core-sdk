package com.r3.conclave.benchmarks.bench;

/**
 * This class implements a benchmark authored by Ryutaro Himeno.
 * The description from the original source file:
 * 
 * This benchmark test program is measuring a cpu performance of floating point
 * operation by a Poisson equation solver.
 *
 * If you have any question, please ask me via email. written by Ryutaro HIMENO,
 * November 26, 2001. Version 3.0 
 * ----------------------------------------------
 * Ryutaro Himeno, Dr. of Eng. Head of Computer Information Division, RIKEN (The
 * Institute of Pysical and Chemical Research) Email :
 * himeno@postman.riken.go.jp
 * --------------------------------------------------------------- 
 * You can adjust the size of this benchmark code to fit your target computer. In that
 * case, please chose following sets of [mimax][mjmax][mkmax]: small : 33,33,65
 * small : 65,65,129 midium: 129,129,257 large : 257,257,513 ext.large:
 * 513,513,1025 This program is to measure a computer performance in MFLOPS by
 * using a kernel which appears in a linear solver of pressure Poisson eq. which
 * appears in an incompressible Navier-Stokes solver. A point-Jacobi method is
 * employed in this solver as this method can be easyly vectrized and be
 * parallelized. 
 * ------------------ 
 * Finite-difference method, curvilinear
 * coodinate system Vectorizable and parallelizable on each grid point No. of
 * grid points : imax x jmax x kmax including boundaries 
 * ------------------
 * A,B,C:coefficient matrix, wrk1: source term of Poisson equation wrk2 :
 * working area, OMEGA : relaxation parameter BND:control variable for
 * boundaries and objects ( = 0 or 1) P: pressure
 *
 *
 * Java version by Stefan Krause
 *
 */
public class Himeno implements Benchmark {

    private static final void MRs(Matrix m, int n, int r, int c, int d,
                                  double val) {
        m.m[(n) * m.mrows * m.mcols * m.mdeps + (r) * m.mcols * m.mdeps + (c)
                * m.mdeps + (d)] = val;
    }

    private static final double MRg(Matrix m, int n, int r, int c, int d) {
        return m.m[(n) * m.mrows * m.mcols * m.mdeps + (r) * m.mcols * m.mdeps + (c) * m.mdeps + (d)];
    }

    @Override
    public void run(String[] args) {
        int i, j, k, nn;
        int imax, jmax, kmax, mimax, mjmax, mkmax, msize[] = new int[3];
        double gosa = 0, target;

        if (args.length != 1) {
            System.out.println("For example: ");
            System.out.println(" Grid-size= XS (32x32x64)");
            System.out.println("\t    S  (64x64x128)");
            System.out.println("\t    M  (128x128x256)");
            System.out.println("\t    L  (256x256x512)");
            System.out.println("\t    XL (512x512x1024)\n");
            System.out.println("Grid-size = ");
            System.out.println("\n");
            System.exit(-1);
        }

        set_param(msize, args[0]);

        mimax = msize[0];
        mjmax = msize[1];
        mkmax = msize[2];
        imax = mimax - 1;
        jmax = mjmax - 1;
        kmax = mkmax - 1;

        target = 60.0;

        //System.out.println("mimax = " + mimax + " mjmax = " + mjmax + " mkmax = " + mkmax);
        //System.out.println("imax = " + imax + " jmax = " + jmax + " kmax =" + kmax);

        /*
         * Initializing matrixes
         */
        newMat(p, 1, mimax, mjmax, mkmax);
        newMat(bnd, 1, mimax, mjmax, mkmax);
        newMat(wrk1, 1, mimax, mjmax, mkmax);
        newMat(wrk2, 1, mimax, mjmax, mkmax);
        newMat(a, 4, mimax, mjmax, mkmax);
        newMat(b, 3, mimax, mjmax, mkmax);
        newMat(c, 3, mimax, mjmax, mkmax);

        mat_set_init(p);
        mat_set(bnd, 0, 1.0);
        mat_set(wrk1, 0, 0.0);
        mat_set(wrk2, 0, 0.0);
        mat_set(a, 0, 1.0);
        mat_set(a, 1, 1.0);
        mat_set(a, 2, 1.0);
        mat_set(a, 3, 1.0 / 6.0);
        mat_set(b, 0, 0.0);
        mat_set(b, 1, 0.0);
        mat_set(b, 2, 0.0);
        mat_set(c, 0, 1.0);
        mat_set(c, 1, 1.0);
        mat_set(c, 2, 1.0);

        /*
         * Start measuring
         */
        /*
         * nn= 3; System.out.format(" Start rehearsal measurement process.\n");
         * System.out.format(" Measure the performance in %d times.\n\n",nn);
         *
         * cpu0= second(); gosa= jacobi(nn,&a,&b,&c,&p,&bnd,&wrk1,&wrk2); cpu1=
         * second(); cpu= cpu1 - cpu0; flop= fflop(imax,jmax,kmax);
         *
         * System.out.format(" MFLOPS: %f time(s): %f %e\n\n",
         * mflops(nn,cpu,flop),cpu,gosa);
         *
         * nn= (int)(target/(cpu/3.0));
         *
         * System.out.format(" Now, start the actual measurement process.\n");
         * System.out.format(" The loop will be excuted in %d times\n",nn);
         * System.out.format(" This will take about one minute.\n"); System.out.format("
         * Wait for a while\n\n");
         */
        nn = 30;
        for (int m = 0; m < 10; m++) {
            gosa = jacobi(nn, a, b, c, p, bnd, wrk1, wrk2);
        }


        /*
         * Matrix free
         */
        clearMat(p);
        clearMat(bnd);
        clearMat(wrk1);
        clearMat(wrk2);
        clearMat(a);
        clearMat(b);
        clearMat(c);

    }

    public static String identifier() {
        return "himeno";
    }

    static class Matrix {
        double[] m;
        int mnums;
        int mrows;
        int mcols;
        int mdeps;
    }

    private static double omega = 0.8;
    private static Matrix a = new Matrix(), b = new Matrix(), c = new Matrix(),
            p = new Matrix(), bnd = new Matrix(), wrk1 = new Matrix(),
            wrk2 = new Matrix();

    public static void main(String[] args) {
    }

    static double fflop(int mx, int my, int mz) {
        return ((double) (mz - 2) * (double) (my - 2) * (double) (mx - 2) * 34.0);
    }

    static double mflops(int nn, double cpu, double flop) {
        return (flop / cpu * 1.e-6 * (double) nn);
    }

    static void set_param(int is[], String size) {
        if ("xxs".equalsIgnoreCase(size)) {
            is[0] = 8;
            is[1] = 8;
            is[2] = 16;
            return;
        }
        if ("xs".equalsIgnoreCase(size)) {
            is[0] = 32;
            is[1] = 32;
            is[2] = 64;
            return;
        }
        if ("s".equalsIgnoreCase(size)) {
            is[0] = 64;
            is[1] = 64;
            is[2] = 128;
            return;
        }
        if ("m".equalsIgnoreCase(size)) {
            is[0] = 128;
            is[1] = 128;
            is[2] = 256;
            return;
        }
        if ("l".equalsIgnoreCase(size)) {
            is[0] = 256;
            is[1] = 256;
            is[2] = 512;
            return;
        }
        if ("xl".equalsIgnoreCase(size)) {
            is[0] = 512;
            is[1] = 512;
            is[2] = 1024;
            return;
        } else {
            System.out.println("Invalid input character " + size + "!!");
            System.exit(-1);
        }
    }

    static void newMat(Matrix Mat, int mnums, int mrows, int mcols, int mdeps) {
        Mat.mnums = mnums;
        Mat.mrows = mrows;
        Mat.mcols = mcols;
        Mat.mdeps = mdeps;
        Mat.m = new double[mnums * mrows * mcols * mdeps];
    }

    static void clearMat(Matrix Mat) {
        Mat.m = null;
        Mat.mnums = 0;
        Mat.mcols = 0;
        Mat.mrows = 0;
        Mat.mdeps = 0;
    }

    static void mat_set(Matrix Mat, int l, double val) {
        int i, j, k;

        for (i = 0; i < Mat.mrows; i++)
            for (j = 0; j < Mat.mcols; j++)
                for (k = 0; k < Mat.mdeps; k++)
                    MRs(Mat, l, i, j, k, val);
    }

    static void mat_set_init(Matrix Mat) {
        int i, j, k;

        for (i = 0; i < Mat.mrows; i++)
            for (j = 0; j < Mat.mcols; j++)
                for (k = 0; k < Mat.mdeps; k++)
                    MRs(Mat, 0, i, j, k, (double) (i * i)
                            / (double) ((Mat.mrows - 1) * (Mat.mrows - 1)));
    }

    static double jacobi(int nn, Matrix a, Matrix b, Matrix c, Matrix p, Matrix bnd,
                         Matrix wrk1, Matrix wrk2) {
        double gosa = 0.0;


        for (int n = 0; n < nn; n++) {
            gosa = innerLoop(a, b, c, p, bnd, wrk1, wrk2);

        } /* end n loop */

        return gosa;
    }

    private static double innerLoop(Matrix a, Matrix b, Matrix c, Matrix p,
                                    Matrix bnd, Matrix wrk1, Matrix wrk2) {
        double gosa = 0.0;
        final int imax = p.mrows - 1;
        final int jmax = p.mcols - 1;
        final int kmax = p.mdeps - 1;
        final int mrows = a.mrows;
        final int mcols = a.mcols;
        final int mdeps = a.mdeps;
        for (int i = 1; i < imax; i++)
            for (int j = 1; j < jmax; j++)
                for (int k = 1; k < kmax; k++) {
                    double s0 = a.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + (i + 1) * mcols * mdeps + j * mdeps + k]
                            + a.m[1 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j + 1) * mdeps + k]
                            + a.m[2 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + (k + 1)]
                            + b.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * (p.m[0 * mrows * mcols * mdeps + (i + 1) * mcols * mdeps + (j + 1) * mdeps + k]
                            - p.m[0 * mrows * mcols * mdeps + (i + 1) * mcols * mdeps + (j - 1) * mdeps + k]
                            - p.m[0 * mrows * mcols * mdeps + (i - 1) * mcols * mdeps + (j + 1) * mdeps + k]
                            + p.m[0 * mrows * mcols * mdeps + (i - 1) * mcols * mdeps + (j - 1) * mdeps + k])
                            + b.m[1 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * (p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j + 1) * mdeps + (k + 1)]
                            - p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j - 1) * mdeps + (k + 1)]
                            - p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j + 1) * mdeps + (k - 1)]
                            + p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j - 1) * mdeps + (k - 1)])
                            + b.m[2 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * (p.m[0 * mrows * mcols * mdeps + (i + 1) * mcols * mdeps + j * mdeps + (k + 1)]
                            - p.m[0 * mrows * mcols * mdeps + (i - 1) * mcols * mdeps + j * mdeps + (k + 1)]
                            - p.m[0 * mrows * mcols * mdeps + (i + 1) * mcols * mdeps + j * mdeps + (k - 1)]
                            + p.m[0 * mrows * mcols * mdeps + (i - 1) * mcols * mdeps + j * mdeps + (k - 1)])
                            + c.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + (i - 1) * mcols * mdeps + j * mdeps + k]
                            + c.m[1 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + (j - 1) * mdeps + k]
                            + c.m[2 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k]
                            * p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + (k - 1)]
                            + wrk1.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k];

                    double ss = (s0 * a.m[3 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k] - p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k])
                            * bnd.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k];

                    gosa += ss * ss;
                    wrk2.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k] = p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k] + omega * ss;
                }

        for (int i = 1; i < imax; i++)
            for (int j = 1; j < jmax; j++)
                for (int k = 1; k < kmax; k++)
                    p.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k] = wrk2.m[0 * mrows * mcols * mdeps + i * mcols * mdeps + j * mdeps + k];
        return gosa;
    }

    static double second() {
        //return System.currentTimeMillis() / 1000.0;
        return 0.0;
    }
}
