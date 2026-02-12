package com.youtube.jpa.repository;

import com.youtube.jpa.dao.ChannelDao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArxivRepository extends JpaRepository<ChannelDao, Long> { // TODO, this has to point to right DAO
}
