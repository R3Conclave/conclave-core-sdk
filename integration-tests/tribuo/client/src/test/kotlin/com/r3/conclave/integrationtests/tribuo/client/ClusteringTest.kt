package com.r3.conclave.integrationtests.tribuo.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.tribuo.math.la.DenseVector

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ClusteringTest : TribuoTest() {
    companion object {
        private lateinit var clustering: Clustering

        @BeforeAll
        @JvmStatic
        fun clusteringSetup() {
            clustering = Clustering(client)
        }
    }

    @Order(0)
    @Test
    fun train() {
        val train = clustering.train(Clustering.centroids, Clustering.iterations, Clustering.distanceType,
                Clustering.numThreads, Clustering.seed)
        assertThat(train.normalizedMI).isEqualTo(0.8128096132028937)
        assertThat(train.adjustedMI).isEqualTo(0.8113314999600718)
    }

    @Order(1)
    @Test
    fun centroids() {
        val centroids = clustering.centroids().centroids
        assertThat(centroids).containsExactly(
            DenseVector.createDenseVector(doubleArrayOf(-1.7294066290817505,-0.019280856227650595)),
            DenseVector.createDenseVector(doubleArrayOf(2.740410056407627,2.8737688541143247)),
            DenseVector.createDenseVector(doubleArrayOf(0.05102068424764918,0.0757660102333321)),
            DenseVector.createDenseVector(doubleArrayOf(5.174977643580621,5.088149544081452)),
            DenseVector.createDenseVector(doubleArrayOf(9.938804461039872,-0.020702060844743055))
        )
    }

    @Order(2)
    @Test
    fun evaluate() {
        val evaluate = clustering.evaluate()
        assertThat(evaluate.normalizedMI).isEqualTo(0.8154291916732408)
        assertThat(evaluate.adjustedMI).isEqualTo(0.8139169342020222)
    }
}