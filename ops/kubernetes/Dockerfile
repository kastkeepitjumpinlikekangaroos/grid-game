# One image for both: the central server (server.jar) and a match server (match_server.jar), from
# their Bazel deploy jars, which build.sh puts beside this file. The worlds are what a match server
# loads its maps from (MatchLauncher.resolveWorldPath: worlds/ under the working directory).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY server.jar match_server.jar /app/
COPY worlds /app/worlds
RUN useradd --system --uid 10001 gridgame && mkdir /data && chown gridgame /data
USER 10001
