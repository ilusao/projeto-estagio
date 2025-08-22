package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.database.Cursor;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private Bancodedados dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new Bancodedados(this);

        webView = findViewById(R.id.webview);
        if (webView == null) {
            Log.e("DEBUG", "WebView está null!");
        } else {
            Log.d("DEBUG", "WebView inicializada corretamente.");
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Liga a interface JS -> Android
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        carregarPaginaFaixas();
    }

    private void carregarPaginaFaixas() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Sempre tenta enviar faixas, mas JS só executa se função existir
                enviarFaixasParaJS();
            }
        });
        webView.setWebChromeClient(new android.webkit.WebChromeClient());
        webView.loadUrl("file:///android_asset/login.html");
    }

    private void enviarFaixasParaJS() {
        Cursor cursor = dbHelper.listarFaixas();
        JSONArray faixasArray = new JSONArray();

        if (cursor.moveToFirst()) {
            do {
                try {
                    JSONObject faixaObj = new JSONObject();
                    faixaObj.put("id", cursor.getInt(cursor.getColumnIndex("id")));
                    faixaObj.put("nome_produto", cursor.getString(cursor.getColumnIndex("produto")));
                    faixaObj.put("tipo_oferta", cursor.getString(cursor.getColumnIndex("tipo_oferta")));
                    faixaObj.put("preco_oferta", cursor.isNull(cursor.getColumnIndex("preco_oferta")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_oferta")));
                    faixaObj.put("preco_normal", cursor.isNull(cursor.getColumnIndex("preco_normal")) ? JSONObject.NULL : cursor.getDouble(cursor.getColumnIndex("preco_normal")));
                    faixaObj.put("estado_faixa", cursor.getString(cursor.getColumnIndex("estado")));
                    faixaObj.put("condicao_faixa", cursor.getString(cursor.getColumnIndex("condicao")));
                    faixaObj.put("comentario", cursor.getString(cursor.getColumnIndex("comentario")));
                    faixaObj.put("limite_cpf", cursor.getInt(cursor.getColumnIndex("limite_cpf")));
                    faixaObj.put("vezes_usada", cursor.getInt(cursor.getColumnIndex("vezes_usada")));

                    String dataCriacao = cursor.getString(cursor.getColumnIndex("data_criacao"));
                    faixaObj.put("tempo_faixa", calcularTempoFaixa(dataCriacao));

                    faixasArray.put(faixaObj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        // JS só executa se função carregarFaixas existir
        String jsCommand = "if (typeof carregarFaixas === 'function') { carregarFaixas(" + faixasArray.toString() + "); }";
        webView.evaluateJavascript(jsCommand, null);
    }

    private String calcularTempoFaixa(String dataCriacao) {
        if (dataCriacao == null) return "0 dias";

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date inicio = sdf.parse(dataCriacao);
            java.util.Date hoje = new java.util.Date();

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

    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void cadastrarFaixa(String produto, String tipo_oferta, String preco_oferta_str, String preco_normal_str,
                                   String estado, String condicao, String comentario, boolean limite_cpf) {

            runOnUiThread(() -> {
                // Validação de preços
                if ((preco_oferta_str == null || preco_oferta_str.trim().isEmpty()) &&
                        (tipo_oferta.equals("sv") || tipo_oferta.equals("cartao") || tipo_oferta.equals("oferta"))) {
                    Toast.makeText(MainActivity.this, "Preço de oferta obrigatório!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if ((preco_normal_str == null || preco_normal_str.trim().isEmpty()) &&
                        (tipo_oferta.equals("sv") || tipo_oferta.equals("cartao"))) {
                    Toast.makeText(MainActivity.this, "Preço normal obrigatório!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Checar se os valores são realmente números
                try {
                    if (!preco_oferta_str.isEmpty()) Double.parseDouble(preco_oferta_str.replace(",", "."));
                    if (!preco_normal_str.isEmpty()) Double.parseDouble(preco_normal_str.replace(",", "."));
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Preços devem ser números válidos!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Inserir no banco com checagem de duplicata
                int resultado = dbHelper.inserirFaixaComDuplicataOpcional(
                        produto,
                        tipo_oferta,
                        preco_oferta_str,
                        preco_normal_str,
                        estado,
                        condicao,
                        comentario,
                        limite_cpf,
                        false // não permitir duplicata inicialmente
                );

                // Tratamento de duplicata
                if (resultado == -2) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Faixa duplicada")
                            .setMessage("Já existe uma faixa igual. Deseja cadastrar mesmo assim?")
                            .setPositiveButton("Sim", (dialog, which) -> {
                                dbHelper.inserirFaixaComDuplicataOpcional(
                                        produto,
                                        tipo_oferta,
                                        preco_oferta_str,
                                        preco_normal_str,
                                        estado,
                                        condicao,
                                        comentario,
                                        limite_cpf,
                                        true // forçar duplicata
                                );
                                Toast.makeText(MainActivity.this, "Faixa duplicada cadastrada!", Toast.LENGTH_SHORT).show();
                                enviarFaixasParaJS();
                            })
                            .setNegativeButton("Não", (dialog, which) -> {
                                Toast.makeText(MainActivity.this, "Cadastro cancelado.", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else if (resultado == 1) {
                    Toast.makeText(MainActivity.this, "Faixa cadastrada com sucesso!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao cadastrar faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }



        @android.webkit.JavascriptInterface
        public void excluirFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.deletarFaixa(id);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa excluída!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao excluir faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void editarFaixa(int id, String produto, String tipoOferta,
                                double precoOferta, double precoNormal,
                                String estado, String condicao,
                                String comentario, int limiteCpf) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.editarFaixa(id, produto, tipoOferta,
                        precoOferta, precoNormal, estado, condicao, comentario, limiteCpf);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa atualizada!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao atualizar faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void usarFaixa(int id, int dias) {
            runOnUiThread(() -> {
                if (dias <= 0) {
                    Toast.makeText(MainActivity.this, "Número de dias inválido!", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean sucesso = dbHelper.iniciarUso(id, dias);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa em uso por " + dias + " dias!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Erro: faixa não encontrada.", Toast.LENGTH_SHORT).show();
                }
                enviarFaixasParaJS();
            });
        }

        @android.webkit.JavascriptInterface
        public void cancelarUsoFaixa(int id) {
            runOnUiThread(() -> {
                boolean sucesso = dbHelper.cancelarUso(id);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Uso da faixa cancelado!", Toast.LENGTH_SHORT).show();
                    enviarFaixasParaJS();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao cancelar uso.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
