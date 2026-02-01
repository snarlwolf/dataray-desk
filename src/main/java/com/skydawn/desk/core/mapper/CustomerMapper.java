package com.skydawn.desk.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.skydawn.desk.core.entity.Customer;

@Mapper
public interface CustomerMapper {
    Customer findById(String id);
    Customer findByWhatsapp(String whatsappNum);
    List<Customer> findAll();
    int insert(Customer customer);
    int update(Customer customer);
    int delete(String id);
}
