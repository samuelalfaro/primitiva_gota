# Compila, prueba y ejecuta ejemplos solo con el JDK (javac/java): Maven es una ayuda, no un requisito.
# Mismo reparto de directorios que Maven (target/classes, target/test-classes, target/imagenes), así que
# ./ejemplos.sh y "make" se pueden alternar con "mvn" sin recompilar de más.
#
#   make                         compila la biblioteca, las pruebas y los ejemplos
#   make test                    ejecuta los niveles rápidos: unitarias + regresion
#   make test NIVELES=estudio    un nivel concreto (unitarias, regresion, estudio); varios separados por comas
#   make test-todo               todos los niveles (las matrices de estudio tardan minutos)
#   make ejemplo EJ=Flor         ejecuta un ejemplo; la imagen queda en target/imagenes
#   make clean                   borra target/
#
# Variables: JAVAC, JAVA (por defecto los del PATH), OUT (target), RELEASE (vacío: el nivel del JDK; RELEASE=17
# fuerza el nivel 17, y necesita lib/ct.sym, que falta en algunos JDK de Ubuntu: en ese caso usa SOURCE=17).
# Solo hace falta un JDK 17 o posterior, y funciona sin entorno gráfico (java.awt.headless=true).

JAVAC   ?= javac
JAVA    ?= java
OUT     ?= target
RELEASE ?=
SOURCE  ?=
NIVELES ?= unitarias,regresion

JFLAGS  = -nowarn -encoding UTF-8 \
          $(if $(RELEASE),--release $(RELEASE)) \
          $(if $(SOURCE),-source $(SOURCE) -target $(SOURCE))
HEADLESS = -Djava.awt.headless=true

MAIN_SRC     := $(shell find src/main/java -name '*.java')
TEST_SRC     := $(shell find src/test/java src/ejemplos/java -name '*.java')
RESOURCES    := $(shell find src/test/resources -type f 2>/dev/null)

CLASSES      = $(abspath $(OUT))/classes
TEST_CLASSES = $(abspath $(OUT))/test-classes
IMAGENES     = $(abspath $(OUT))/imagenes
SEP          = :

.PHONY: all test test-todo ejemplo clean

all: $(TEST_CLASSES)/.compilado

$(CLASSES)/.compilado: $(MAIN_SRC)
	@mkdir -p $(CLASSES)
	@echo "javac: biblioteca -> $(CLASSES)"
	@$(JAVAC) $(JFLAGS) -d $(CLASSES) $(MAIN_SRC)
	@touch $@

# Los ejemplos se compilan con las pruebas (como hace Maven): usan utilidades de src/test.
$(TEST_CLASSES)/.compilado: $(CLASSES)/.compilado $(TEST_SRC) $(RESOURCES)
	@mkdir -p $(TEST_CLASSES)
	@echo "javac: pruebas y ejemplos -> $(TEST_CLASSES)"
	@$(JAVAC) $(JFLAGS) -cp $(CLASSES) -d $(TEST_CLASSES) $(TEST_SRC)
	@if [ -d src/test/resources ]; then cp -r src/test/resources/. $(TEST_CLASSES)/; fi
	@touch $@

# Todas lanza cada suite en su JVM, con target/imagenes como directorio de trabajo (hay que lanzarla desde aquí).
test: all
	@mkdir -p $(IMAGENES)
	$(JAVA) $(HEADLESS) -Dgota.fuentes=$(CURDIR)/src/main/java -cp $(CLASSES)$(SEP)$(TEST_CLASSES) \
	    common.Todas $(CLASSES)$(SEP)$(TEST_CLASSES) $(NIVELES)

test-todo:
	$(MAKE) test NIVELES=unitarias,regresion,estudio

ejemplo: all
	@test -n "$(EJ)" || { echo "uso: make ejemplo EJ=Flor"; exit 2; }
	@mkdir -p $(IMAGENES)
	cd $(IMAGENES) && $(JAVA) $(HEADLESS) -cp $(CLASSES)$(SEP)$(TEST_CLASSES) $(EJ)

clean:
	rm -rf $(OUT)
