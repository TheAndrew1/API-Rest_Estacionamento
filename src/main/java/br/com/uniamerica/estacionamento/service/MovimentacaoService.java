package br.com.uniamerica.estacionamento.service;

import br.com.uniamerica.estacionamento.entity.Condutor;
import br.com.uniamerica.estacionamento.entity.Configuracao;
import br.com.uniamerica.estacionamento.entity.Movimentacao;
import br.com.uniamerica.estacionamento.repository.CondutorRepository;
import br.com.uniamerica.estacionamento.repository.ConfiguracaoRepository;
import br.com.uniamerica.estacionamento.repository.MovimentacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class MovimentacaoService {
    @Autowired
    private CondutorRepository condutorRepository;
    @Autowired
    private MovimentacaoRepository movimentacaoRepository;
    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

//    else{     //atualizar tempo do condutor
//        if(!condutor.getTempoPago().equals(Duration.of(0, ChronoUnit.HOURS))){
//            Configuracao configuracao = this.configuracaoRepository.findById(1L).orElse(null);
//            Assert.notNull(configuracao, "Configuração não encontrada!");
//
//            Long times = condutor.getTempoPago().dividedBy(configuracao.getTempoParaDesconto());
//            condutor.setTempoDesconto(Duration.of(configuracao.getTempoDesconto().multipliedBy(times)), ChronoUnit.HOURS);
//        }
//    }

    public Movimentacao findById(final Long id){
        return this.movimentacaoRepository.findById(id).orElse(null);
    }

    public List<Movimentacao> findAll(){
        return this.movimentacaoRepository.findAll();
    }

    public List<Movimentacao> findByAberto(){
        return this.movimentacaoRepository.findBySaidaIsNull();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cadastrar(final Movimentacao movimentacao, Boolean... editado){
        Configuracao configuracao = this.configuracaoRepository.findById(1L).orElse(null);
        Assert.notNull(configuracao, "Configurações do sistema não encontradas!");

        if(editado.length != 0) {
            //Assert.notNull(movimentacao.getSaida(), "Saída não cadastrada!");
            Duration tempo = Duration.between(movimentacao.getEntrada(), movimentacao.getSaida());
            movimentacao.setTempo(tempo.toMinutes());

            tempo = Duration.between(LocalDateTime.of(LocalDate.now(),configuracao.getHorarioFecha()), movimentacao.getSaida());
            if(tempo.isNegative()) {
                movimentacao.setTempoMulta(0L);
                movimentacao.setValorMulta(BigDecimal.ZERO);
            }
            else {
                movimentacao.setTempoMulta(tempo.toMinutes());
                movimentacao.setValorMulta(configuracao.getValorMultaMinuto().multiply(BigDecimal.valueOf(tempo.toMinutes())));
            }

            final Condutor condutor = movimentacao.getCondutor();

            if (movimentacao.getTempoDesconto() != 0) {
                Assert.isTrue(movimentacao.getTempoDesconto() > 0,"Tempo de desconto não pode ser negativo!");
                Assert.isTrue(condutor.getTempoDesconto() > 0,"Condutor não possui tempo de desconto para aplicar!");
                Assert.isTrue(condutor.getTempoDesconto() > movimentacao.getTempoDesconto(),"Condutor não possui tempo de desconto suficiente!");

                movimentacao.setValorDesconto(configuracao.getValorMinuto().multiply(BigDecimal.valueOf(movimentacao.getTempoDesconto())));
            }
            else {
                movimentacao.setTempoDesconto(0L);
                movimentacao.setValorDesconto(BigDecimal.ZERO);
            }

            movimentacao.setValor(configuracao.getValorMinuto().multiply(BigDecimal.valueOf(movimentacao.getTempo())));
            movimentacao.setValorTotal(movimentacao.getValor().add(movimentacao.getValorMulta()).subtract(movimentacao.getValorDesconto()));

            condutor.setTempoPago(condutor.getTempoPago() + movimentacao.getTempo() - movimentacao.getTempoDesconto());
            condutor.setTempoDesconto(((condutor.getTempoPago()/configuracao.getTempoParaDesconto())*configuracao.getTempoDesconto()) - movimentacao.getTempoDesconto());
            this.condutorRepository.save(condutor);

            movimentacao.setAtivo(false);
        }

        this.movimentacaoRepository.save(movimentacao);
    }

    @Transactional(rollbackFor = Exception.class)
    public void editar(final Long id, final Movimentacao movimentacao){
        Movimentacao movimentacaoDatabase = findById(id);
        Assert.notNull(movimentacaoDatabase, "Movimentação não encontrada!");
        Assert.isTrue(movimentacaoDatabase.isAtivo(), "Movimentação já fechada!");
        Assert.isTrue(movimentacaoDatabase.getId().equals(movimentacao.getId()), "Movimentações não conferem!");

        cadastrar(movimentacao, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public String excluir(final Long id){
        Movimentacao movimentacao = findById(id);
        Assert.notNull(movimentacao, "Movimentação não encontrada!");

        movimentacao.setAtivo(false);
        this.movimentacaoRepository.save(movimentacao);
        return "Movimentação está inativa!";
    }
}
