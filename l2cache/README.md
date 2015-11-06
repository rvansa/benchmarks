# Hibernate Benchmarks

These benchmarks are intended to measure Hibernate ORM performance with or without 2nd level cache.

## Build

You need to specify version of Hibernate against which this should be built, there are several options:
* -Dhibernate.42 (note that there are some code changes in BenchmarkBase related to Criteria API when using Hibernate 4.2.x)
* -Dhibernate.43
* -Dhibernate.50 (uses snapshot version)
* -Dhibernate.51 (uses snapshot version)

Entities are *not* enhanced by default, you can turn this on during build using -Dhibernate.enhanced

Possible JDBC driver implementations added to `benchmarks.jar` are controled using profiles triggered by these options:
* -Dh2 (on by default)
* -Dpostgres (on by default)
* -Dderby
* -Dp6spy (allows you to log the SQL)

In addition to these, the DB calls can be partially mocked, more about this below. The mocking tooling is not
released to Maven repository, you need to checkout the sources and build it yourselves - SNAPSHOT versions are used
in pom.xml. You can find the source here:

https://github.com/rvansa/perfmock/

In case that `perfmock` uses snapshot version of `mockrunner` (not at the time of writing this readme), try to look for it here:

https://github.com/rvansa/mockrunner

## Run

This benchmarks are based on the [JMH](http://openjdk.java.net/projects/code-tools/jmh/) tool so we encourage
to study its documentation. Basic run has this form:

```
java -jar target/benchmarks.jar -wi WARMUP_IT -i IT -f FORK -t THREADS -p FOO=BAR BENCHMARK
```

with this meaning:
* `WARMUP_IT`: number of warmup iterations (not included into results)
* `IT`: number of iterations (included into results)
* `FORK`: number of forked executions - to remove the effect of different JVM layout in memory
   it is recommended to run the test in forked JVM, several times.
* `FOO`: property defined in the benchmark as a field annotated by `@Param`, `BAR` is its value
* `BENCHMARK`: substring of the class_name.method_name that selects which benchmarks are ran
* `THREADS`: number of concurrent threads executing the benchmark. Note that there are some limits
   on connection pools that can affect multi-threaded results

So an instance of this command can look like

```
java -jar target/benchmarks.jar -wi 10 -i 10 -f 5 -p persistenceUnit=c3p0 PersonBenchmark.testRead
```

The defaults for warmup iterations/iterations/forks are 20/20/10 but the values above work well for us.
For debugging you have to set number of forks to 0.

## Parameters

* persistenceUnit: Selects one of the persistence units defined there. See persistence.xml for all options. Note that
          some of those persistence units require standalone running DB (not started by the test).
* dbSize: Approximate number of entities in the DB. The actual number of entities in DB can change
          during the test (e.g. as we are creating/deleting entities). Default is 10,000.
* batchLoadSize: Number of entities used in batch when (re)initializing the DB contents. Default is 100.
* transactionSize: Number of entities created/read/updated/deleted in single transaction. Default is 10.
* secondLevelCache: Variant of the second level cache used in this benchmark. Default is 'none', other options
          set the default cache concurrency strategy to `TRANSACTIONAL` ('tx'), `READ_WRITE` ('rw'), `READ_ONLY` ('ro')
          or NONSTRICT_READ_WRITE ('ns'). Note that with local cache, transactional/read-write/read-only should not
          make any difference.
* useTx: Wrap all invocations on EntityManager with transactions. Default is 'true'. If the persistence unit ends
          with '.xa' or 2nd level cache in Hibernate 4.x is used, these will be JTA transactions, otherwise transactions
          on EntityManager will be used.
* queryCache: Turn on/off the query cache in 2nd level cache. Default is 'true'.
* directReferenceEntries: Value for 'hibernate.cache.use_reference_entries'. Default is 'true'.
* minimalPuts: Value for 'hibernate.cache.use_minimal_puts'. Default is 'false' (this variant should be more performant with Infinispan).
* lazyLoadNoTrans; Value for 'hibernate.enable_lazy_load_no_trans'. Default is 'false'.

There are another parameters specific to each benchmark, these will be mentioned later.

## Mocking

In order to isolate Hibernate/Infinispan performance and DB performance, this benchmark uses `perfmock` and `mockrunner`
libraries to provide JDBC driver that gives results without going to DB itself. Mocking is triggered setting
`persistenceUnit` that contains word 'mock' - this should use connection URL starting with 'jdbc:perfmock:'.
Usually, Hibernate initializes against real DB but for the test itself we switch to use the mocks.

As some column names are hardcoded in the mocked expressions, it's possible that with some version of Hibernate
the results won't match. In that case please turn on SQL logging (e.g. in persistenceUnit set `hibernate.show_sql`
to `true` and adapt the contents of `setupMock()` method in given benchmark.

## Benchmarks

### PersonBenchmark
Person: auto-generated id, 3 string attributes
Besides CRUD operations either via em.persist there are Criteria API-based queries which query 1, 2 or all 3 attributes.

### ConstantBenchmark:
Constant: auto-generated id, single string attribute, tagged as immutable.
Designed to use with 2nd level cache. In order to simulate < 100% cache hit ratio, the set of entity IDs is changed:
a) in mocked case, you can disable this by `-p mutate=false` ('true' is default). You can also set
   `mutationPeriod` and `mutationCount` (defaults are 100 ms/1000).
b) in non-mocked case, you have to uncomment some annotations - please refer to ConstantBenchmark javadoc.

### DogBenchmark
Mammal - Dog - Beagle: joined inheritance, auto-generated id, 1 string attribute each

### EmployerBenchmark
Employer - Employees: ManyToOne relationship, besides that both contain auto-generated id and single string attribute.

### HundredBenchmark
Hundred: auto-generated id and 100 integer attributes.
The purpose of this entity is to show the effect of entity enhancement.

### NodeBenchmark
Node: auto-generated id, root mark and two OneToOne relationships, with precomputed sizes of the subtree.
Test for operations on binary trees stored in DB (not really an effective representation).
Note that this benchmark is *not* properly mocked.
