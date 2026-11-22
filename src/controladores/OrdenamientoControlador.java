package controladores;

import modelos.Documento;
import servicios.CronometroServicio;
import servicios.DocumentoServicio;
import vistas.OrdenamientoVista;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OrdenamientoControlador {

    private OrdenamientoVista vista;

    public OrdenamientoControlador(OrdenamientoVista vista) {
        this.vista = vista;

        this.vista.setOrdenarBurbuja(evento -> ordenarBurbuja());
        this.vista.setOrdenarRapido(evento -> ordenarRapido());
        this.vista.setOrdenarInsercion(evento -> ordenarInsercion());
        this.vista.setOrdenarMezcla(evento -> ordenarMezcla());
        this.vista.setBuscar(evento -> buscar());

        var rutaArchivo = DocumentoServicio.encontrarRutaCsv();
        DocumentoServicio.cargar(rutaArchivo);
        mostrar();
    }

    private void mostrar(){
        var datos=DocumentoServicio.getDatosDocumentos();
        vista.mostrarDatos(DocumentoServicio.getEncabezados(), datos);
    }

    private void ordenarBurbuja() {
        ejecutarOrdenamiento(() -> ordenamientoBurbuja(new ArrayList<>(DocumentoServicio.getDocumentos())));
    }

    private void ordenarRapido() {
        ejecutarOrdenamiento(() -> {
            List<Documento> documentos = new ArrayList<>(DocumentoServicio.getDocumentos());
            ordenamientoRapido(documentos, 0, documentos.size() - 1);
            DocumentoServicio.setDocumentos(documentos);
        });
    }

    private void ordenarInsercion() {
        ejecutarOrdenamiento(() -> ordenamientoInsercion(new ArrayList<>(DocumentoServicio.getDocumentos())));
    }

    private void ordenarMezcla() {
        ejecutarOrdenamiento(() -> {
            List<Documento> documentos = new ArrayList<>(DocumentoServicio.getDocumentos());
            DocumentoServicio.setDocumentos(
                    ordenamientoMezcla(documentos, 0, documentos.size() - 1));
        });
    }

    private void ejecutarOrdenamiento(Runnable algoritmo) {
        CronometroServicio.iniciarCronometro();
        vista.setTiempo(CronometroServicio.getTextoTiempoCronometro());
        vista.setOrdenamientoEnCurso(true);

        Timer actualizadorTiempo = new Timer(1000,
                evento -> vista.setTiempo(CronometroServicio.getTextoTiempoCronometro()));
        actualizadorTiempo.start();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                algoritmo.run();
                return null;
            }

            @Override
            protected void done() {
                actualizadorTiempo.stop();
                try {
                    get();
                    vista.setTiempo(CronometroServicio.getTextoTiempoCronometro());
                    mostrar();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(vista, "Se interrumpió el ordenamiento.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(vista,
                            "No se pudo completar el ordenamiento: " + e.getCause().getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    vista.setOrdenamientoEnCurso(false);
                }
            }
        }.execute();
    }

    private String clave(Documento documento) {
        String nombreCompleto = documento.getApellido1() + " " + documento.getApellido2()
                + " " + documento.getNombre();
        String documentoTipo = documento.getDocumento();

        if (vista.getCriterio() == 0) {
            return normalizar(nombreCompleto) + "\n" + normalizar(documentoTipo);
        }
        return normalizar(documentoTipo) + "\n" + normalizar(nombreCompleto);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }

    private void ordenamientoBurbuja(List<Documento> documentos) {
        for (int i = 0; i < documentos.size() - 1; i++) {
            boolean intercambio = false;
            for (int j = 0; j < documentos.size() - i - 1; j++) {
                if (clave(documentos.get(j)).compareTo(clave(documentos.get(j + 1))) > 0) {
                    Documento temporal = documentos.get(j);
                    documentos.set(j, documentos.get(j + 1));
                    documentos.set(j + 1, temporal);
                    intercambio = true;
                }
            }
            if (!intercambio) {
                break;
            }
        }
        DocumentoServicio.setDocumentos(documentos);
    }

    private void ordenamientoInsercion(List<Documento> documentos) {
        for (int i = 1; i < documentos.size(); i++) {
            Documento actual = documentos.get(i);
            int j = i - 1;
            while (j >= 0 && clave(documentos.get(j)).compareTo(clave(actual)) > 0) {
                documentos.set(j + 1, documentos.get(j));
                j--;
            }
            documentos.set(j + 1, actual);
        }
        DocumentoServicio.setDocumentos(documentos);
    }

    private void ordenamientoRapido(List<Documento> documentos, int inicio, int fin) {
        if (inicio >= fin) {
            return;
        }
        int indicePivote = particionar(documentos, inicio, fin);
        ordenamientoRapido(documentos, inicio, indicePivote - 1);
        ordenamientoRapido(documentos, indicePivote + 1, fin);
    }

    private int particionar(List<Documento> documentos, int inicio, int fin) {
        Documento pivote = documentos.get(fin);
        int i = inicio - 1;

        for (int j = inicio; j < fin; j++) {
            if (clave(documentos.get(j)).compareTo(clave(pivote)) <= 0) {
                i++;
                Documento temporal = documentos.get(i);
                documentos.set(i, documentos.get(j));
                documentos.set(j, temporal);
            }
        }

        Documento temporal = documentos.get(i + 1);
        documentos.set(i + 1, documentos.get(fin));
        documentos.set(fin, temporal);
        return i + 1;
    }

    private List<Documento> ordenamientoMezcla(List<Documento> documentos, int inicio, int fin) {
        if (inicio >= fin) {
            return documentos;
        }

        int medio = inicio + (fin - inicio) / 2;
        ordenamientoMezcla(documentos, inicio, medio);
        ordenamientoMezcla(documentos, medio + 1, fin);
        mezclar(documentos, inicio, medio, fin);
        return documentos;
    }

    private void mezclar(List<Documento> documentos, int inicio, int medio, int fin) {
        List<Documento> izquierda = new ArrayList<>(documentos.subList(inicio, medio + 1));
        List<Documento> derecha = new ArrayList<>(documentos.subList(medio + 1, fin + 1));
        int i = 0;
        int j = 0;
        int k = inicio;

        while (i < izquierda.size() && j < derecha.size()) {
            if (clave(izquierda.get(i)).compareTo(clave(derecha.get(j))) <= 0) {
                documentos.set(k++, izquierda.get(i++));
            } else {
                documentos.set(k++, derecha.get(j++));
            }
        }
        while (i < izquierda.size()) {
            documentos.set(k++, izquierda.get(i++));
        }
        while (j < derecha.size()) {
            documentos.set(k++, derecha.get(j++));
        }
    }

    private void buscar() {
        String texto = vista.getTextoBuscar();
        if (texto == null || texto.trim().isEmpty()) {
            mostrar();
            return;
        }

        String filtro = normalizar(texto);
        List<Documento> encontrados = DocumentoServicio.getDocumentos().stream()
                .filter(documento -> normalizar(documento.getNombreCompleto()).contains(filtro)
                        || normalizar(documento.getDocumento()).contains(filtro))
                .collect(java.util.stream.Collectors.toList());

        String[][] datos = convertirDatos(encontrados);
        vista.mostrarDatos(DocumentoServicio.getEncabezados(), datos);
    }

    private String[][] convertirDatos(List<Documento> documentos) {
        String[][] datos = new String[documentos.size()][5];
        for (int i = 0; i < documentos.size(); i++) {
            Documento documento = documentos.get(i);
            datos[i] = new String[]{
                    String.valueOf(i + 1),
                    documento.getApellido1(),
                    documento.getApellido2(),
                    documento.getNombre(),
                    documento.getDocumento()
            };
        }
        return datos;
    }

}
