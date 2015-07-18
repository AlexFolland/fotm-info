package info.fotm.clustering

import com.github.nscala_time.time.Imports._
import info.fotm.clustering.ClusteringEvaluatorData.DataPoint
import info.fotm.clustering.FeatureSettings.features
import info.fotm.clustering.enhancers._
import info.fotm.clustering.implementations.RMClustering.EqClusterer2
import info.fotm.clustering.implementations._

object ClusteringEvaluatorApp extends App {
  val evaluator = new ClusteringEvaluator(features)

  for ((_, settings) <- Defaults.settings) {
    println(s"Evaluating $settings:")
    val dataGen: ClusteringEvaluatorData = new ClusteringEvaluatorData(settings)
    val data: Stream[DataPoint] = dataGen.updatesStream().slice(500, 700)

    def createCMV(turns: Int, threshold: Int): (String, RealClusterer) = {
      s"C * M($turns, $threshold) * V" ->
        new ClonedClusterer(RealClusterer.wrap(new ClosestClusterer)) with Multiplexer with Verifier {
          override lazy val multiplexTurns = turns
          override lazy val multiplexThreshold = threshold
        }
    }

    val clusterers: Map[String, RealClusterer] = Map(
      //        "Random" -> RealClusterer.wrap(new RandomClusterer),
      "HT3" -> RealClusterer.wrap(new HTClusterer3),
      "HT2" -> RealClusterer.wrap(new HTClusterer2),
      "HT2 * V" -> new ClonedClusterer(RealClusterer.wrap(new HTClusterer2)) with Verifier,
      "HT3 * V" -> new ClonedClusterer(RealClusterer.wrap(new HTClusterer3)) with Verifier,
      "HT3[RM]" -> RealClusterer.wrap(new HTClusterer3(Some(new EqClusterer2))),
      "HT3[RM] * V" -> new ClonedClusterer(RealClusterer.wrap(new HTClusterer3(Some(new EqClusterer2)))) with Verifier,
      "RM" -> RealClusterer.wrap(new EqClusterer2),
      "RM * V" -> new ClonedClusterer(RealClusterer.wrap(new EqClusterer2)) with Verifier,
      createCMV(20, 3),
      "(HT3 + CM) * V" -> new Summator(
        RealClusterer.wrap(new HTClusterer3),
        new ClonedClusterer(RealClusterer.wrap(new ClosestClusterer)) with Multiplexer
      ) with Verifier,
      "(HT3[RM] + CM) * V" -> new Summator(
        RealClusterer.wrap(new HTClusterer3(Some(new EqClusterer2))),
        new ClonedClusterer(RealClusterer.wrap(new ClosestClusterer)) with Multiplexer
      ) with Verifier
    )

    for ((name, clusterer) <- clusterers.par) {
      val startTime = DateTime.now.toInstant

      val result = evaluator.evaluate(clusterer, data)

      val elapsed = new Period(startTime, DateTime.now)
      println(s"$name = $result, time: $elapsed")
    }
  }
}
