# Chapter 03 Kafka������--��Kafkaд������


## 3.1 �����߸���

��Ϣ���͹���
1. ���л�: ProducerRecord�����Key��Value�����л�, ����������
    * **���л���** (Serializer): ���л���Ϣ
2. ����: 
    1. ���ProducerRecord������ָ���˷���, ��ѡ��÷���
    2. �������Key���������
    3. ��¼����ӵ�һ����¼����(batch), ��һ���̷߳���
    * **������** (Partitioner): �����������
3. **Broker**������Ϣ
    * д��ɹ�: ����**RecordMetaData**
        * RecordMetaData: ������Ϣ����Topic, Partition, offset
    * д��ʧ��: ���ش�����Ϣ, �����߿��ܻ�����, ����ʧ���ٷ��ش��� 
    ![](high-level-overview-of-Kafka-producer-components.jpg)


## 3.2 ����Kafka������

�����߻�������:
```properties
# broker��ַ�嵥
bootstrap.servers = broker1:9092,broker2:9092
# ����:
    # �ɶ�ѡ�񼸸�broker��ֹ����һЩ崻�

# ���л���(ȫ�޶���)
key.serializer = org.apache.kafka.common.serialization.StringSerializer
value.serializer = org.apache.kafka.common.serialization.StringSerializer
    # ����:
        # ByteArraySerializer (Ĭ��)
        # IntegerSerializer
        # StringSerializer
        # �û��Զ���
```

��Ϣ��ʽ: *�������һ��3.3*
* ���Ͳ�����
* ͬ������
* �첽����

## 3.3 ������Ϣ��Kafka

��Ϣ��ʽ
```java
    ProducerRecord<String, String>record = 
        new ProducerRecord<>("topicName","key","value");

    // ���Ͳ�����
    try{
        producer.send(record);
    }catch(Exception e){}

    // ͬ������
    try{
        producer.send(record).get(); // ������ȡ�ý��
    }catch(Exception e){}

    // �첽����, �ص�
    producer.send(
        record, 
        (recordMetadata, e)->{}
    );

// ��ؽӿ�
    Future<RecordMetadata> send(ProducerRecord<K, V> record);
    Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback);
    // ����:
        // ����Ϣ�ŵ�������
        // �õ����̷߳���ProducerRecord����
        // ���ع�����Future�������ڲ�ѯ���
    // ���ܵ��쳣:
        // SerializationException
        // BufferExhaustedException
        // TimeoutException
        // InterruptException

    // org.apache.kafka.clients.producer.Callback
    public interface Callback{
        void onCompletion(RecordMetadata metadata, Exception exception);
    }
```

## 3.4 �����ߵ�����

����
```properties
# ������Ҫ�����Ϣ���ճɹ�����
acks = 1
# ��ѡֵ:
    # 0: ����Ҫ��������Ӧ
    # 1: ȷ������ڵ��յ���Ϣ
        # ��Leader��δ��ѡ����, Ҳ�ᶪʧ, �������ط�
    # all: ȷ�����нڵ��յ���Ϣ (�ȫ, ��ʱ��ɱ����)

# 
buffer.memory = 1
# ���������
    # �ɰ汾: �������쳣, ȡ����block.on.buffer.full
    # �°汾: ������ max.block.ms������

compression.type = snappy
# Ĭ��: ��ѹ��
# ��ѡֵ:
    # snappy: CPUռ�ý���
    # gzip: ѹ���ʸ�, ռ�ý϶�CPU
    # lz4

# ���Դ���
retries = 1
# ��ز���: ����ǰ�ȴ�ʱ�� retry.backoff.ms 
# ����: ���Դ��� * ���Եȴ�ʱ�� Ӧ�ô���Kafka�ı����ָ�ʱ��

# ���δ�С, ��λΪbyte, �ﵽ����
batch.size = 1024
# ����:
    # ̫��: ռ�ڴ�; ̫С: Ƶ�ʷ���ռ������, ������С

# ??? �Ǵﵽlinger.ms�ͷ���(����batch.size), ����˵�ﵽ���δ�С��, ����ǰ�ĵȴ�ʱ��
linger.ms = 1

# ���ٸ�������Ӧһ��
max.inflight.requests.per.connection = 3
# "ÿ�����ӵ���������"
# ��Ϊ1�ɱ�֤��Ϣ��˳��

# brokerͬ��ʱ�ȴ���Ӧ�����ʱ��
timout.ms
    # ��acksƥ��, ��Ӧ����ʱ�䳬��timeout.ms����Ϊ��ʧ
# Producer����Message��Brokerʱ�ȴ���Ӧ�����ʱ��
request.timout.ms
# Producer����Ԫ����ʱ�ȴ���Ӧ�����ʱ��
metadata.fetch.timeout.ms

```


