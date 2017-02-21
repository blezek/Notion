
# the revision of our software as git thinks of it
tag := $(shell git describe --abbrev=4 --dirty --always --tags)

# How to parse the revision.
# Makefiles must quote # (hash), \ (backslash) and () (parens)
re := s\#\\([0-9]*\\)[.]\\([0-9]*\\)[.]\\([0-9]*\\)\\([.0-9A-Za-z-]*\\)\#

# Now pull off each element
build.major := $(shell echo $(tag)| sed -e "$(re)\\1\#")
build.minor := $(shell echo $(tag)| sed -e "$(re)\\2\#")
build.patch := $(shell echo $(tag)| sed -e "$(re)\\3\#")
build.meta := $(shell echo $(tag)| sed -e "$(re)\\4\#")

build=$(build.major).$(build.minor).$(build.patch)
versionDir=Notion-$(build)
dir=zip-temp/$(versionDir)
DATE := $(shell /bin/date +%F-%T)


define help

Makefile for Notion $(build)
  install - install Notion on qia server
  dist    - build a zipped distribution file
  build   - build UI, documentation and Jar file
  sync    - rsync the current build to qia server
  restart - restart Notion on the qia server

endef
export help

help:
	@echo "$$help"

dist: build
	./gradlew jar
	rm -rf ./zip-temp
	mkdir -p $(dir)
	rsync -r build/libs/lib $(dir)
	rsync notion $(dir)
	rsync build/libs/Notion.jar $(dir)/Notion.jar
	rsync Readme.md $(dir)
	rsync notion.example.yml $(dir)/notion.yml
	rsync -r Documentation/_build/html $(dir)/Documentation
	(cd zip-temp && zip -r $(versionDir).zip $(versionDir) && mv $(versionDir).zip ../)

build:
	rm -rf src/main/resources/public
	(cd ui/ && make clean install)
	(cd Documentation && make clean install)

install: dist
	${MAKE} sync
	${MAKE} restart

sync:
	rsync -arvz zip-temp/$(versionDir)/ qin@qia:/research/images/Notion/$(versionDir)-$(DATE)
	ssh qin@qia "cd /research/images/Notion ;ln -sfn $(versionDir)-$(DATE) Notion"

restart:
	ssh -t qia sudo /sbin/service notion restart

.PHONY: build dist install watch server sync restart
