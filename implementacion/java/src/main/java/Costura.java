/**
 * Dónde caen las costuras entre las zonas de la tira (cuerpo y tapas), común a colores y texturas: cómo se reparte
 * el interior en líneas de t constante, que van del contorno al eje y al otro contorno.
 * Valen para círculos y elipses, con solape PLANO y TRAZO.
 */
public enum Costura {
    /** Las líneas de t constante son normales al camino; las tapas son semidiscos. */
    ORTOGONAL,
    /** Por defecto. Cada mitad de la línea es el radio al punto de tangencia (recta, en espiga); tapas polares. */
    ESPIGA,
    /** Como ESPIGA pero cada mitad es un Hermite (normal en el eje, radio en el contorno): sin picos en el eje. */
    SUAVIZADO
}
