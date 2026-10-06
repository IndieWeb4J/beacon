FROM gcr.io/distroless/java25-debian13

WORKDIR /app
COPY beacon.jar /app/beacon.jar

EXPOSE 8080
CMD ["/app/beacon.jar"]
