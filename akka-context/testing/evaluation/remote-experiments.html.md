<!-- <nav> -->
- [Akka](../../index.html)
- [Testing](../index.html)
- [Evaluation](index.html)
- [Remote experiments](remote-experiments.html)

<!-- </nav> -->

# Remote experiments

Feature set: Evaluations Contact [our support](https://akka.io/contact) for access.
*This functionality evolves quickly, the behavior and APIs might change between releases without further notice.* A remote experiment runs on the offline-evals service instead of in the customer’s JVM.
The experiment setup the customer builds is the same setup the runner reads locally.
The service owns the durability, the paging, and the retries.
The report the customer fetches back is the same report a local run would produce.

## <a href="about:blank#_when_to_use_a_remote_run"></a> When to use a remote run

- Datasets over a few thousand eval cases.
- Experiments that take more than a few minutes locally.
- Experiments whose cost should be attributed to a shared budget rather than a developer’s local model spend.
- Experiments that need to run on a schedule.

## <a href="about:blank#_how_it_works"></a> How it works

`akka.evalkit.client.RemoteCampaign` uploads the dataset, submits the experiment spec to the service, and polls for the finished record.
The service’s `ExperimentWorkflow` owns durability, paging and retries. `RemoteCampaign` lives in the `akka.evalkit.client` package alongside `OfflineEvalsClient`, evalkit’s crossing point to the running service. `RemoteCampaign.run(httpClients, spec)` takes an Akka SDK `HttpClientProvider` and a `Spec` naming the service, the dataset and the eval cases.

A local run and a remote run are separate entry points, not one switch. `ExperimentRunner.run` executes in process. `RemoteCampaign.run` posts an experiment to the service and polls for its record.

## <a href="about:blank#_submitting_from_the_cli"></a> Submitting from the CLI

akka eval experiments apply -f my-experiment.yaml The service returns an experiment id. Follow progress with:

akka eval experiments get <experiment-id> Fetch the final record:

akka eval experiments record <experiment-id>
## <a href="about:blank#_service_endpoints"></a> Service endpoints

The client speaks these endpoints:

- `POST /api/datasets/imports`. Upload the dataset.
- `GET  /api/datasets/imports/{id}`. Import progress.
- `POST /api/experiments`. Start the experiment.
- `GET  /api/experiments/{id}`. Status, including the reasons a refused run gives.
- `GET  /api/experiments/{id}/record`. Final record, once one has been written.
- `GET  /api/experiments/{id}/compare/{baseline}`. Comparison.
The service also serves `GET /api/experiments/{id}/items/{itemId}`, `GET /api/experiments/history/{name}`, `GET /api/experiments/evaluators` and `GET /api/experiments/models`, which the client does not call.

The default endpoint is set with `EVAL_URL`.

## <a href="about:blank#_best_practices"></a> Best practices

- Prefer local runs while iterating on evaluators and datasets.
- Use remote runs for scheduled experiments and for anything larger than a laptop can drive comfortably.
- Attribute experiment runs to a named budget on the service.

<!-- <footer> -->
<!-- <nav> -->
[Baselines and regression](baselines.html) [Red teaming](../red-teaming/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->