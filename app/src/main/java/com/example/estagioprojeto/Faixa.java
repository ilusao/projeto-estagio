package com.example.estagioprojeto;

public class Faixa {
    private String produto;
    private String tipoOferta;
    private double precoOferta;
    private double precoNormal;
    private String estado;
    private String condicao;
    private String comentario;
    private int limiteCpf;
    private int usando;
    private int diasUso;
    private int diasRestantes;
    private int vezesUsada;
    private String dataCadastro;

    // Construtor
    public Faixa(String produto, String tipoOferta, double precoOferta, double precoNormal,
                 String estado, String condicao, String comentario, int limiteCpf) {
        this.produto = produto;
        this.tipoOferta = tipoOferta;
        this.precoOferta = precoOferta;
        this.precoNormal = precoNormal;
        this.estado = estado;
        this.condicao = condicao;
        this.comentario = comentario;
        this.limiteCpf = limiteCpf;
    }

    public Faixa() {
    }

    // Getters
    public String getProduto() { return produto; }
    public String getTipoOferta() { return tipoOferta; }
    public double getPrecoOferta() { return precoOferta; }
    public double getPrecoNormal() { return precoNormal; }
    public String getEstado() { return estado; }
    public String getCondicao() { return condicao; }
    public String getComentario() { return comentario; }
    public int getLimiteCpf() { return limiteCpf; }

    // Setters (se precisar editar depois)
    public void setProduto(String produto) { this.produto = produto; }
    public void setTipoOferta(String tipoOferta) { this.tipoOferta = tipoOferta; }
    public void setPrecoOferta(double precoOferta) { this.precoOferta = precoOferta; }
    public void setPrecoNormal(double precoNormal) { this.precoNormal = precoNormal; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setCondicao(String condicao) { this.condicao = condicao; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public void setLimiteCpf(int limiteCpf) { this.limiteCpf = limiteCpf; }

    public int getUsando() { return usando; }
    public void setUsando(int usando) { this.usando = usando; }

    public int getDiasUso() { return diasUso; }
    public void setDiasUso(int diasUso) { this.diasUso = diasUso; }

    public int getDiasRestantes() { return diasRestantes; }
    public void setDiasRestantes(int diasRestantes) { this.diasRestantes = diasRestantes; }

    public int getVezesUsada() {
        return vezesUsada;
    }

    public void setVezesUsada(int vezesUsada) {
        this.vezesUsada = vezesUsada;
    }

    public String getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(String dataCadastro) { this.dataCadastro = dataCadastro; }
}
