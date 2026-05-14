build-apk:
	docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit
	@echo "APK ready at output/memo-release.apk"

clean:
	rm -rf output/*.apk android

.PHONY: build-apk clean
