# Tribuo tutorials

This project shows how to run [Tribuo Machine Learning](https://tribuo.org) algorithms with Conclave.

The code is based on [Tribuo's tutorials](https://tribuo.org/learn/4.0/tutorials/), which cover the following examples:
* [Classification](https://tribuo.org/learn/4.0/tutorials/irises-tribuo-v4.html)
* [Clustering](https://tribuo.org/learn/4.0/tutorials/clustering-tribuo-v4.html)
* [Regression](https://tribuo.org/learn/4.0/tutorials/regression-tribuo-v4.html)
* [Anomaly Detection](https://tribuo.org/learn/4.0/tutorials/anomaly-tribuo-v4.html)
* [Configuration](https://tribuo.org/learn/4.0/tutorials/configuration-tribuo-v4.html)

In this sample the client is using Mail to communicate with the enclave.
Each mail contains a serialized request for the enclave to execute some action, i.e.,
training models and obtaining test evaluation results.

The training and test data is kept private, as well as the results returned by the enclave.

The code has been converted to Kotlin and uses Kotlin serialization to serialize requests
and responses to and from the enclave.

To facilitate unit testing each tutorial has been split among several methods which
are expected to run in a certain order, as each step produces state for the following calls.

The data files have been obtained from the following locations:
* [bezdekIris.data](https://archive.ics.uci.edu/ml/machine-learning-databases/iris/bezdekIris.data)
* [winequality-red.csv](https://archive.ics.uci.edu/ml/machine-learning-databases/wine-quality/winequality-red.csv)
* [train-images-idx3-ubyte.gz](http://yann.lecun.com/exdb/mnist/train-images-idx3-ubyte.gz)
* [train-labels-idx1-ubyte.gz](http://yann.lecun.com/exdb/mnist/train-labels-idx1-ubyte.gz)
* [t10k-images-idx3-ubyte.gz](http://yann.lecun.com/exdb/mnist/t10k-images-idx3-ubyte.gz)
* [t10k-labels-idx1-ubyte.gz](http://yann.lecun.com/exdb/mnist/t10k-labels-idx1-ubyte.gz)

## Unit tests
The tests are validating the output according to Oracle's documentation at the time of Tribuo 4.0.1.
The purpose of the tests is to ensure Conclave's GraalVM is returning the expected results.
This project exercises features such as reflection, serialization and the in-memory file system.
The values have been obtained from the tutorials themselves and are the same when running on
mock mode or in the enclave. Changes to Conclave, without changing Tribuo version,
which lead to different results will most likely mean a bug has been introduced in Conclave.
Changes to the Tribuo version may result in different output or results, meaning we may need to update the asserted values.
