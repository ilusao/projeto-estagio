package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import android.util.Log;
import android.database.Cursor;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.io.File;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.os.Environment;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private Bancodedados dbHelper;
    private long backPressedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        dbHelper = new Bancodedados(this);

        // Popular banco se vazio
        if (dbHelper.listarFaixas().getCount() == 0) {
            dbHelper.popularBancoDeFaixas();
            Log.d("DEBUG_DB", "Cadastro inicial das faixas concluído!");
        }

        configurarWebView();
        atualizarEstados();
        enviarFaixasVelhasParaJS(); // gráfico inicial
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed();
            } else {
                Toast.makeText(this, "Pressione novamente para sair", Toast.LENGTH_SHORT).show();
            }
            backPressedTime = System.currentTimeMillis();
        }
    }

    // ----------------------------- CONFIG WEBVIEW ----------------------------- //
    private void configurarWebView() {
        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url.contains("menu.html")) {
                    enviarFaixasVelhasParaJS();
                } else if (url.contains("login.html")) {
                    enviarFaixasParaJS();
                } else if (url.contains("VerFaixa.html")) {
                    enviarFaixasPesquisaParaJS("");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WEBVIEW_LOG", consoleMessage.message() + " -- " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/login.html");
    }

    // ----------------------------- ATUALIZA ESTADOS ----------------------------- //
    private void atualizarEstados() {
        Cursor cursor = dbHelper.listarFaixas();
        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndex("id"));
            String dataCadastro = cursor.getString(cursor.getColumnIndex("data_criacao"));
            String estadoAtual = cursor.getString(cursor.getColumnIndex("estado"));

            // Calcula novo estado com base na data
            String novoEstado = calcularEstado(dataCadastro, estadoAtual);

            // Atualiza apenas se houver mudança e não retroceder
            if (!novoEstado.equals(estadoAtual)) {
                dbHelper.atualizarEstado(id, novoEstado);
            }
        }
        cursor.close();
    }

    // ----------------------------- CÁLCULOS ----------------------------- //
    public String calcularEstado(String dataCadastro, String estadoAtual) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date data = sdf.parse(dataCadastro);
            Date hoje = new Date();
            long diff = hoje.getTime() - data.getTime();
            long dias = TimeUnit.MILLISECONDS.toDays(diff);
            long meses = dias / 30; // meses aproximados

            // Nova faixa (estado null) inicia como Nova
            if (estadoAtual == null) {
                if (meses < 1) return "Nova";
                if (meses < 3) return "Nem nova nem velha";
                return "Velha";
            }

            // Mantém estado existente, só evoluindo
            switch (estadoAtual) {
                case "Nova":
                    if (meses >= 1 && meses < 3) return "Nem nova nem velha";
                    if (meses >= 3) return "Velha";
                    return "Nova"; // menos de 1 mês continua Nova
                case "Nem nova nem velha":
                    if (meses >= 3) return "Velha";
                    return "Nem nova nem velha";
                case "Velha":
                    return "Velha"; // nunca retrocede
                default:
                    return estadoAtual; // qualquer outro valor mantém
            }
        } catch (Exception e) {
            e.printStackTrace();
            return estadoAtual != null ? estadoAtual : "Nova";
        }
    }

    public String calcularTempoFaixa(String dataCriacao) {
        if (dataCriacao == null) return "0 dias";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date inicio = sdf.parse(dataCriacao);
            Date hoje = new Date();
            long diff = hoje.getTime() - inicio.getTime();
            long dias = diff / (1000L * 60 * 60 * 24);

            long anos = dias / 360;
            dias %= 360;
            long meses = dias / 30;
            dias %= 30;
            long semanas = dias / 7;
            dias %= 7;

            StringBuilder sb = new StringBuilder();
            if (anos > 0) sb.append(anos).append(anos == 1 ? " ano " : " anos ");
            if (meses > 0) sb.append(meses).append(meses == 1 ? " mês " : " meses ");
            if (semanas > 0) sb.append(semanas).append(semanas == 1 ? " semana " : " semanas ");
            if (dias > 0) sb.append(dias).append(dias == 1 ? " dia" : " dias");

            String resultado = sb.toString().trim();
            String[] partes = resultado.split(" ");
            if (partes.length > 4) {
                resultado = partes[0] + " " + partes[1] + " e " + partes[2] + " " + partes[3];
            }
            return resultado.isEmpty() ? "0 dias" : resultado;

        } catch (Exception e) {
            return "0 dias";
        }
    }

    // ----------------------------- GERAR JSON ----------------------------- //
    private JSONArray gerarJSONArray(Cursor cursor) {
        JSONArray array = new JSONArray();
        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id", cursor.getInt(cursor.getColumnIndex("id")));
                    obj.put("codigo_faixa", cursor.getString(cursor.getColumnIndex("codigo_faixa")));
                    obj.put("nome_produto", cursor.getString(cursor.getColumnIndex("produto")));
                    obj.put("tipo_oferta", cursor.getString(cursor.getColumnIndex("tipo_oferta")));
                    obj.put("preco_oferta", cursor.isNull(cursor.getColumnIndex("preco_oferta")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_oferta")));
                    obj.put("preco_normal", cursor.isNull(cursor.getColumnIndex("preco_normal")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_normal")));
                    obj.put("estado_faixa", cursor.getString(cursor.getColumnIndex("estado")));
                    obj.put("condicao_faixa", cursor.getString(cursor.getColumnIndex("condicao")));
                    obj.put("comentario", cursor.getString(cursor.getColumnIndex("comentario")));
                    obj.put("limite_cpf", cursor.getInt(cursor.getColumnIndex("limite_cpf")));
                    obj.put("vezes_usada", cursor.getInt(cursor.getColumnIndex("vezes_usada")));
                    obj.put("usando", cursor.getInt(cursor.getColumnIndexOrThrow("usando")));
                    obj.put("dias_uso", cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso")));
                    obj.put("data_inicio_uso", cursor.getString(cursor.getColumnIndexOrThrow("data_inicio_uso")));
                    obj.put("tempo_faixa", calcularTempoFaixa(cursor.getString(cursor.getColumnIndex("data_criacao"))));

                    // Cálculo dias restantes
                    String dataInicioUso = cursor.getString(cursor.getColumnIndexOrThrow("data_inicio_uso"));
                    int diasUso = cursor.getInt(cursor.getColumnIndexOrThrow("dias_uso"));
                    long diasRestantes = 0;
                    if (dataInicioUso != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        Date inicio = sdf.parse(dataInicioUso);
                        long diff = new Date().getTime() - inicio.getTime();
                        long diasPassados = diff / (1000L * 60 * 60 * 24);
                        diasRestantes = Math.max(diasUso - diasPassados, 0);
                    }
                    obj.put("dias_restantes", diasRestantes);

                    array.put(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return array;
    }

    // ----------------------------- ENVIAR PARA JS ----------------------------- //
    private void enviarFaixasParaJS() {
        Cursor cursor = dbHelper.listarFaixas();
        JSONArray array = gerarJSONArray(cursor);
        webView.evaluateJavascript("if (typeof carregarFaixas === 'function') { carregarFaixas(" + array.toString() + "); }", null);
    }

    private void enviarFaixasPesquisaParaJS(String texto) {
        // Pega a lista de faixas do banco
        List<Faixa> lista = dbHelper.pesquisarFaixa(texto);

        // Cria um JSONArray
        JSONArray array = new JSONArray();
        for (Faixa f : lista) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", f.getId());
                obj.put("codigo_faixa", f.getCodigoFaixa());
                obj.put("nome_produto", f.getProduto());
                obj.put("tipo_oferta", f.getTipoOferta());
                obj.put("preco_oferta", f.getPrecoOferta());
                obj.put("preco_normal", f.getPrecoNormal());
                obj.put("estado_faixa", f.getEstado());
                obj.put("condicao_faixa", f.getCondicao());
                obj.put("comentario", f.getComentario());
                obj.put("limite_cpf", f.getLimiteCpf());
                obj.put("usando", f.getUsando());
                obj.put("dias_uso", f.getDiasUso());
                obj.put("data_inicio_uso", f.getDataInicioUso());
                obj.put("vezes_usada", f.getVezesUsada());
                obj.put("tempo_faixa", f.getTempoFaixa());
                array.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Envia para o WebView
        webView.evaluateJavascript(
                "if (typeof carregarFaixas === 'function') { carregarFaixas(" + array.toString() + "); }",
                null
        );

    }



    private void enviarFaixasVelhasParaJS() {
        Cursor cursor = dbHelper.listarFaixas();
        JSONArray array = new JSONArray();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                JSONObject faixa = new JSONObject();
                try {
                    faixa.put("nome", cursor.getString(cursor.getColumnIndex("produto")));
                    faixa.put("estado", cursor.getString(cursor.getColumnIndex("estado")));
                    faixa.put("condicao", cursor.getString(cursor.getColumnIndex("condicao")));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                array.put(faixa);
            }
            cursor.close();
        }

        // Apenas armazena os dados em JS, não abre modal
        String jsCode = "window.dadosFaixasVelhas = " + array.toString() + ";";
        webView.evaluateJavascript(jsCode, null);
    }

    // ----------------------------- WEBAPPINTERFACE ----------------------------- //
    private class WebAppInterface {

        @JavascriptInterface
        public void cadastrarFaixa(String produto, String tipo_oferta, String preco_oferta_str,
                                   String preco_normal_str, String estado, String condicao,
                                   String comentario, boolean limite_cpf) {
            runOnUiThread(() -> {
                // 🔹 Valida preços obrigatórios
                if ((preco_oferta_str == null || preco_oferta_str.trim().isEmpty()) &&
                        (tipo_oferta.equalsIgnoreCase("sv") ||
                                tipo_oferta.equalsIgnoreCase("Cartão") ||
                                tipo_oferta.equalsIgnoreCase("oferta"))) {
                    Toast.makeText(MainActivity.this, "Preço de oferta obrigatório!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if ((preco_normal_str == null || preco_normal_str.trim().isEmpty()) &&
                        (tipo_oferta.equalsIgnoreCase("sv") ||
                                tipo_oferta.equalsIgnoreCase("Cartão"))) {
                    Toast.makeText(MainActivity.this, "Preço normal obrigatório!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 🔹 Valida números
                try {
                    if (preco_oferta_str != null && !preco_oferta_str.trim().isEmpty()) {
                        Double.parseDouble(preco_oferta_str.replace(",", "."));
                    }
                    if (preco_normal_str != null && !preco_normal_str.trim().isEmpty()) {
                        Double.parseDouble(preco_normal_str.replace(",", "."));
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Preços devem ser números válidos!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 🔹 Define o estado final
                String estadoFinal = (estado == null || estado.trim().isEmpty()) ? "Nova" : normalizarEstado(estado);

                // 🔹 Insere no banco, passando 0 para código → gera automaticamente
                int resultado = dbHelper.inserirFaixaComDuplicataOpcional(
                        0, // 0 indica que o método vai gerar o próximo código disponível
                        produto, tipo_oferta, preco_oferta_str, preco_normal_str,
                        estadoFinal, condicao, comentario, limite_cpf, false
                );

                if (resultado == -2) {
                    // Já existe → pergunta se quer cadastrar mesmo assim
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Faixa duplicada")
                            .setMessage("Já existe uma faixa igual. Deseja cadastrar mesmo assim?")
                            .setPositiveButton("Sim", (dialog, which) -> {
                                dbHelper.inserirFaixaComDuplicataOpcional(
                                        0, produto, tipo_oferta, preco_oferta_str, preco_normal_str,
                                        estadoFinal, condicao, comentario, limite_cpf, true
                                );
                                Toast.makeText(MainActivity.this, "Faixa duplicada cadastrada!", Toast.LENGTH_SHORT).show();
                                enviarFaixasParaJS(); // envia todas as faixas para JS
                            })
                            .setNegativeButton("Não", (dialog, which) ->
                                    Toast.makeText(MainActivity.this, "Cadastro cancelado.", Toast.LENGTH_SHORT).show())
                            .show();
                } else if (resultado == 1) {
                    Toast.makeText(MainActivity.this, "Faixa cadastrada com sucesso!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao cadastrar faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void exportarFaixas() {
            runOnUiThread(() -> {
                try {
                    // 🔹 Onde salvar o arquivo → Downloads
                    File pasta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!pasta.exists()) pasta.mkdirs();

                    File arquivo = new File(pasta, "faixa.txt");

                    boolean ok = dbHelper.exportarFaixasParaTxt(arquivo);

                    if (ok) {
                        Toast.makeText(MainActivity.this,
                                "Backup salvo em: " + arquivo.getAbsolutePath(),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Erro ao gerar backup.",
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this,
                            "Falha no backup: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void excluirFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.deletarFaixa(id);
                Toast.makeText(MainActivity.this, sucesso ? "Faixa excluída!" : "Erro ao excluir faixa.", Toast.LENGTH_SHORT).show();
                enviarFaixasParaJS();
            });
        }

        @JavascriptInterface
        public void editarFaixa(int id, String produto, String tipoOferta,
                                double precoOferta, double precoNormal,
                                String estado, String condicao,
                                String comentario, int limiteCpf) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.editarFaixa(id, produto, tipoOferta, precoOferta, precoNormal, estado, condicao, comentario, limiteCpf);
                Toast.makeText(MainActivity.this, sucesso ? "Faixa atualizada!" : "Erro ao atualizar faixa.", Toast.LENGTH_SHORT).show();
                enviarFaixasParaJS();
            });
        }

        @JavascriptInterface
        public void usarFaixa(int id, int dias) {
            runOnUiThread(() -> {
                if (dias <= 0) { Toast.makeText(MainActivity.this, "Número de dias inválido!", Toast.LENGTH_SHORT).show(); return; }
                boolean sucesso = dbHelper.iniciarUso(id, dias);
                if (sucesso) dbHelper.incrementarUso(id);
                Toast.makeText(MainActivity.this, sucesso ? "Faixa em uso por " + dias + " dias!" : "Erro: faixa não encontrada.", Toast.LENGTH_SHORT).show();
                enviarFaixasParaJS();
            });
        }

        @JavascriptInterface
        public void cancelarUsoFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.cancelarUso(id);
                Toast.makeText(MainActivity.this, sucesso ? "Uso da faixa cancelado!" : "Erro ao cancelar uso.", Toast.LENGTH_SHORT).show();
                enviarFaixasParaJS();
            });
        }

        @JavascriptInterface
        public void pesquisarFaixa(String texto) {
            List<Faixa> lista = dbHelper.pesquisarFaixa(texto);
            JSONArray array = new JSONArray();

            for (Faixa f : lista) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("codigo_faixa", f.getCodigoFaixa());
                    obj.put("nome_produto", f.getProduto());
                    obj.put("tipo_oferta", f.getTipoOferta());
                    obj.put("preco_oferta", f.getPrecoOferta());
                    obj.put("preco_normal", f.getPrecoNormal());
                    obj.put("estado_faixa", f.getEstado());
                    obj.put("condicao_faixa", f.getCondicao());
                    obj.put("comentario", f.getComentario());
                    obj.put("limite_cpf", f.getLimiteCpf());
                    obj.put("usando", f.getUsando());
                    obj.put("dias_uso", f.getDiasUso());
                    obj.put("data_inicio_uso", f.getDataInicioUso());
                    obj.put("vezes_usada", f.getVezesUsada());
                    obj.put("tempo_faixa", f.getTempoFaixa());
                    array.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            webView.post(() -> webView.evaluateJavascript(
                    "if (typeof carregarFaixas === 'function') { carregarFaixas(" + array.toString() + "); }",
                    null));
        }


        @JavascriptInterface
        public void filtrarFaixas(String filtroJson) {
            runOnUiThread(() -> {
                try {
                    JSONObject filtro = new JSONObject(filtroJson);
                    Cursor cursor = dbHelper.filtrarFaixas(
                            filtro.optString("tipo", ""),
                            filtro.optString("estado", ""),
                            filtro.optString("condicao", ""),
                            filtro.optString("limite_cpf", ""),
                            filtro.optString("em_uso", "")
                    );
                    JSONArray array = gerarJSONArray(cursor);
                    webView.evaluateJavascript("if (typeof carregarFaixas === 'function') { carregarFaixas(" + array.toString() + "); }", null);
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        @JavascriptInterface
        public void navegarPara(String pagina) {
            runOnUiThread(() -> webView.loadUrl("file:///android_asset/" + pagina));
        }

        // ----------------------------- NORMALIZAR ESTADO ----------------------------- //
        private String normalizarEstado(String estado) {
            if (estado == null) return "Nova"; // padrão se não vier nada

            String e = estado.trim().toLowerCase();

            switch (e) {
                case "nova":
                case "novo":
                case "n":
                    return "Nova";

                case "velha":
                case "velho":
                case "antiga":
                case "antigo":
                case "v":
                    return "Velha";

                case "nem nova nem velha":
                case "medio":
                case "regular":
                case "mais ou menos":
                case "nvv":
                    return "Nem nova nem velha";

                default:
                    return "Nova"; // fallback de segurança
            }
        }

        // ----------------------------- GRÁFICOS ----------------------------- //
        @JavascriptInterface
        public int getFaixasParadas1Mes() {
            return dbHelper.contarFaixasParadasUltimoMes();
        }

        @JavascriptInterface
        public String getFaixasParadasDetalhes() {
            List<Faixa> faixas = dbHelper.getFaixasParadasUltimoMes();
            JSONArray array = new JSONArray();
            for (Faixa f : faixas) {
                try { JSONObject obj = new JSONObject(); obj.put("produto", f.getProduto()); obj.put("vezes_usada", f.getVezesUsada()); array.put(obj); }
                catch (JSONException e) { e.printStackTrace(); }
            }
            return array.toString();
        }

        @JavascriptInterface
        public String getFaixasVelhas() {
            List<Faixa> faixas = dbHelper.getFaixasVelhas();
            JSONArray array = new JSONArray();

            // Pega a data de hoje
            LocalDate hoje = LocalDate.now();

            for (Faixa f : faixas) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("produto", f.getProduto());
                    obj.put("estado", f.getEstado());
                    obj.put("data_criacao", f.getDataCadastro());

                    // Converte a data do banco para LocalDate (formato YYYY-MM-DD)
                    String dataCriacaoStr = f.getDataCadastro();
                    if (dataCriacaoStr != null && !dataCriacaoStr.isEmpty()) {
                        LocalDate dataCriacao = LocalDate.parse(dataCriacaoStr);
                        long dias = ChronoUnit.DAYS.between(dataCriacao, hoje);
                        obj.put("dias_uso", dias); // <-- adiciona no JSON
                    } else {
                        obj.put("dias_uso", JSONObject.NULL);
                    }

                    array.put(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return array.toString();
        }

        @JavascriptInterface
        public String getTop10Faixas() {
            List<Faixa> faixas = dbHelper.getTop10Faixas();
            JSONArray jsonArray = new JSONArray();

            try {
                for (Faixa f : faixas) {
                    JSONObject obj = new JSONObject();
                    obj.put("produto", f.getProduto());
                    obj.put("estado", f.getEstado());
                    obj.put("vezes_usada", f.getVezesUsada());
                    obj.put("data_criacao", f.getDataCadastro());
                    obj.put("condicao", f.getCondicao()); // necessário para filtro JS
                    jsonArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return jsonArray.toString();
        }

        @JavascriptInterface
        public String getFaixasParaLixo() {
            List<Faixa> faixas = dbHelper.getFaixasParaLixo();
            JSONArray jsonArray = new JSONArray();

            try {
                for (Faixa f : faixas) {
                    JSONObject obj = new JSONObject();
                    obj.put("produto", f.getProduto());
                    obj.put("estado", f.getEstado());
                    obj.put("vezes_usada", f.getVezesUsada());
                    obj.put("data_criacao", f.getDataCadastro());
                    obj.put("condicao", f.getCondicao()); // necessário
                    jsonArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return jsonArray.toString();
        }

        @JavascriptInterface
        public String getFaixasCadastradasSemana() {
            // usa o novo método que retorna faixas da semana atual e passada
            List<Faixa> faixas = dbHelper.getFaixasPorSemana();
            JSONArray jsonArray = new JSONArray();

            try {
                for (Faixa f : faixas) {
                    JSONObject obj = new JSONObject();
                    obj.put("produto", f.getProduto());
                    obj.put("estado", f.getEstado() != null ? f.getEstado() : ""); // algumas faixas podem não ter estado
                    obj.put("vezes_usada", f.getVezesUsada());
                    obj.put("data_criacao", f.getDataCadastro());
                    obj.put("condicao", f.getCondicao() != null ? f.getCondicao() : "");
                    jsonArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return jsonArray.toString();
        }

    }
}
