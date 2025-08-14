package com.example.estagioprojeto;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.database.Cursor;
import android.widget.Toast;

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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Liga a interface JS -> Android para comunicação
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        carregarPaginaFaixas();
    }


    private void carregarPaginaFaixas() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
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

                            // Calcular anos_faixa
                            int anosFaixa = 0;
                            String dataCriacao = cursor.getString(cursor.getColumnIndex("data_criacao"));
                            if (dataCriacao != null) {
                                try {
                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                    java.util.Date dataInicio = sdf.parse(dataCriacao);
                                    long diff = new java.util.Date().getTime() - dataInicio.getTime();
                                    anosFaixa = (int) (diff / (1000L * 60 * 60 * 24 * 365));
                                } catch (Exception e) {
                                    anosFaixa = 0;
                                }
                            }
                            faixaObj.put("anos_faixa", anosFaixa);

                            faixasArray.put(faixaObj);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();

                String jsCommand = "javascript:carregarFaixas(" + faixasArray.toString() + ")";
                view.evaluateJavascript(jsCommand, null);
            }
        });
        webView.setWebChromeClient(new android.webkit.WebChromeClient());
        webView.loadUrl("file:///android_asset/VerFaixa.html");
    }


    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void excluirFaixa(int id) {
            Log.d("WebAppInterface", "Excluir faixa chamada com id: " + id);
            boolean existeAntes = false;
            Cursor c = dbHelper.listarFaixas();
            if(c.moveToFirst()){
                do{
                    if(c.getInt(c.getColumnIndex("id")) == id){
                        existeAntes = true;
                    }
                }while(c.moveToNext());
            }
            c.close();
            Log.d("WebAppInterface", "Faixa existe no banco antes da exclusão? " + existeAntes);

            runOnUiThread(() -> {
                boolean sucesso = dbHelper.deletarFaixa(id);
                Log.d("WebAppInterface", "Sucesso ao deletar? " + sucesso);
                if (sucesso) {
                    Toast.makeText(MainActivity.this, "Faixa excluída!", Toast.LENGTH_SHORT).show();
                    atualizarListaFaixas();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao excluir faixa.", Toast.LENGTH_SHORT).show();
                }
            });
        }



        private void atualizarListaFaixas() {
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
                        faixaObj.put("anos_faixa", 0);
                        faixasArray.put(faixaObj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();

            // Chama função JS direto, sem recarregar a página
            String jsCommand = "javascript:carregarFaixas(" + faixasArray.toString() + ")";
            webView.evaluateJavascript(jsCommand, null);
        }

    }
}