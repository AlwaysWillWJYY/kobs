package com.kob.matchsystem.service.impl.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
@Component
public class MatchPool extends Thread {
    private static List<Player> players = new ArrayList<>();

    private ReentrantLock lock = new ReentrantLock();

    private static RestTemplate restTemplate;

    private final static String startGameUrl = "http://127.0.0.1:3000/pk/start/game/";

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate){
        MatchPool.restTemplate = restTemplate;
    }
    public void addPlayer(Integer userId, Integer rating, Integer botId){
        lock.lock();;
        try {
            players.add(new Player(userId, rating,botId, 0));
        }finally {
            lock.unlock();
        }
    }

    public void removePlayer(Integer userId){
        lock.lock();;
        try {
            List<Player> newPlayers = new ArrayList<>();
            for(int i = 0; i < players.size(); i++){
                if(!players.get(i).getUserId().equals(userId)){
                    newPlayers.add(players.get(i));
                }
            }
            players = newPlayers;
        }finally {
            lock.unlock();
        }
    }

    private void increaseWaitingTime(){//所有玩家等待时间加1
        for(Player p : players){
            p.setWaitingTime(p.getWaitingTime() + 1);
        }
    }

    private boolean checkMatched(Player a, Player b){
        //判断两名玩家是否匹配
        int ratingDelta = Math.abs(a.getRating() - b.getRating());
        int waitingTime = Math.min(a.getWaitingTime(), b.getWaitingTime());
        return ratingDelta <= waitingTime * 10;
    }
    private void sendResult(Player a, Player b){
        //返回a和b的匹配结果
        System.out.println("send Result: " + a + " " + b);
        MultiValueMap<String, String > data = new LinkedMultiValueMap<>();
        data.add("a_id",a.getUserId().toString());
        data.add("a_bot_id", a.getBotId().toString());
        data.add("b_id",b.getUserId().toString());
        data.add("b_bot_id", b.getBotId().toString());
        restTemplate.postForObject(startGameUrl, data, String.class);
    }
    private void matchPlayers(){
        //尝试匹配所有玩家
        boolean[] vis = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++){
            if(vis[i]){
                continue;
            }
            for(int j = i + 1; j < players.size(); j++){
                if(vis[j]){
                    continue;
                }
                Player a = players.get(i);
                Player b = players.get(j);
                if(checkMatched(a, b)){
                    vis[i] = vis[j] = true;
                    sendResult(a, b);
                    break;
                }
            }
        }
        List<Player> newPlayers = new ArrayList<>();
        for(int i = 0; i < players.size(); i++){
            if(!vis[i]){
                newPlayers.add(players.get(i));
            }
        }
        players = newPlayers;
    }

    @Override
    public void run() {
        while (true){
            try {
                Thread.sleep(1000);
                lock.lock();
                try {
                    increaseWaitingTime();
                    matchPlayers();
                }finally {
                    lock.unlock();
                }
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }
}
