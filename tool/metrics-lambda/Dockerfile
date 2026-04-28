# AWS Lambda container image for the metrics-lambda handler.
#
# Built by `./gradlew :metrics-lambda:buildImage` (which depends on the
# `fatJar` task and runs `docker build` on this Dockerfile).
#
# Base: AWS's java21 Lambda runtime image. Provides /lambda-entrypoint.sh
# which expects the handler reference as CMD.
FROM public.ecr.aws/lambda/java:21

# git is required at runtime: BundleMaterialiser shells out to
# `git clone`, `git checkout`, `git clean` per invocation.
RUN dnf install -y git && dnf clean all

# The handler's fat jar (handler classes + every runtime dep). Lambda's
# Java runtime auto-loads `/var/task/lib/*.jar` and `/var/task/*.jar` onto
# the classpath, so a single jar here is enough.
COPY build/libs/metrics-lambda-*-all.jar /var/task/lib/handler.jar

# Fully-qualified handler reference. Lambda runtime invokes
# Handler.handleRequest(InputStream, OutputStream, Context).
CMD ["com.github.ethanhosier.metricslambda.Handler"]

# Cold-start follow-up (PR 6 measurement task):
#  - Pre-warm Gradle's wrapper cache so the first `./gradlew build` per
#    container doesn't pay distribution-download time.
#  - Bake `~/.gradle/caches/modules-2/` populated with the dep closure of
#    a representative project (the bigger of the two leverages relative
#    to SnapStart).
